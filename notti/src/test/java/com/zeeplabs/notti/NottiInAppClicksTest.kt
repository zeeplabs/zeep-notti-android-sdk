package com.zeeplabs.notti

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** INAPP-10..12, design D2: the coordinator's default sink delivers each click to the host listeners, synchronously. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiInAppClicksTest {
    private val server = MockWebServer()
    private val order = mutableListOf<String>()
    private val logs = mutableListOf<String>()
    private lateinit var coordinator: NottiInAppCoordinator

    private val presenter =
        object : InAppPresenter {
            val presented = mutableListOf<InAppDisplay>()

            override fun present(display: InAppDisplay) {
                presented += display
            }

            override fun close(displayId: String) {
                order += "close"
            }

            override fun teardown(display: InAppDisplay) = Unit
        }

    @Before
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.path.orEmpty().endsWith("/in-app-messages/eligible")) {
                        MockResponse().setResponseCode(200).setBody(
                            """{"messages":[{"id":"msg-fixture","title":"T","trigger":{"type":"session_start"}}]}""",
                        )
                    } else {
                        MockResponse().setResponseCode(201).setBody("""{"duplicate":false}""")
                    }
            }
        server.start()
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("notti_inapp_clicks_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        coordinator =
            NottiInAppCoordinator(
                store = NottiInAppEventStore(prefs),
                clientFactory = { config -> NottiApiClient(OkHttpClient(), config.baseUrl, config.appId, config.clientKey) },
                identity = { InAppIdentity("device-fixture", "token-fixture") },
                executor =
                    object : InAppExecutor {
                        override fun execute(task: () -> Unit) = task()

                        override fun schedule(
                            delayMs: Long,
                            task: () -> Unit,
                        ) = Unit
                    },
                postToMain = { it() },
                logger = { logs += it },
            )
        coordinator.presenter = presenter
    }

    @After
    fun tearDown() {
        NottiInAppClicks.listeners.clearForTest()
        server.shutdown()
    }

    /** A visible message, ready to be tapped. */
    private fun visibleDisplay(): InAppDisplay {
        coordinator.onInitialized("app-fixture", "key-fixture", server.url("/").toString().trimEnd('/'))
        coordinator.session.markRegistered()
        coordinator.onSessionStarted()
        val display = presenter.presented.single()
        coordinator.onDisplayAttached(display.displayId, true)
        coordinator.onDisplayVisible(display.displayId)
        return display
    }

    @Test
    fun everyListenerGetsTheClickBeforeTheMessageCloses() {
        val received = mutableListOf<NottiInAppClick>()
        val thread = Thread.currentThread()
        NottiInAppClicks.listeners.add {
            assertTrue(Thread.currentThread() === thread)
            received += it
            order += "a"
        }
        NottiInAppClicks.listeners.add { order += "b" }
        val display = visibleDisplay()

        coordinator.onDisplayClicked(display.displayId, "b1", InAppAction("deeplink", "app://fixture"))

        assertEquals(listOf("a", "b", "close"), order)
        val expected = NottiInAppClick("msg-fixture", "b1", NottiInAppAction(NottiInAppAction.Type.DEEPLINK, "app://fixture"))
        assertEquals(listOf(expected), received)
        assertTrue(logs.none { it == NottiInAppCoordinator.NO_LISTENER_WARNING })
    }

    @Test
    fun aPushPermissionTapReachesTheListenerWithNoAction() {
        val received = mutableListOf<NottiInAppClick>()
        NottiInAppClicks.listeners.add { received += it }
        val display = visibleDisplay()

        coordinator.onDisplayClicked(display.displayId, "b1", InAppAction(InAppAction.PUSH_PERMISSION, ""))

        assertNull(received.single().action)
    }

    @Test
    fun aThrowingListenerDoesNotStopTheOthersNorTheClose() {
        NottiInAppClicks.listeners.add { throw IllegalStateException("sentinel-detail") }
        NottiInAppClicks.listeners.add { order += "b" }
        val display = visibleDisplay()

        coordinator.onDisplayClicked(display.displayId, "b1", null)

        assertEquals(listOf("b", "close"), order)
        assertTrue(logs.none { it == NottiInAppCoordinator.NO_LISTENER_WARNING })
    }

    @Test
    fun aRemovedListenerGetsNothingAndTheUnhandledClickIsWarned() {
        var calls = 0
        val subscription = NottiInAppClicks.listeners.add { calls++ }
        subscription.remove()
        subscription.remove()
        val display = visibleDisplay()

        coordinator.onDisplayClicked(display.displayId, "b1", InAppAction("deeplink", "app://fixture"))

        assertEquals(0, calls)
        assertEquals(listOf("close"), order)
        assertTrue(logs.contains(NottiInAppCoordinator.NO_LISTENER_WARNING))
    }
}
