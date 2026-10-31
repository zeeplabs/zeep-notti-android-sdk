package com.zeeplabs.notti

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * INAPP-11, INAPP-13..15, design D3: the `onDisplayClicked` change of the ported coordinator. Kept apart from
 * the ported `NottiInAppCoordinatorTest`, whose coordinators have no URL opener.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiInAppCoordinatorUrlTest {
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

    /** Accepts what [accepted] says; records every query and open. */
    private inner class RecordingOpener(
        private val accepted: Boolean,
    ) : InAppUrlOpener {
        override var enabled = true
        val queried = mutableListOf<String>()

        override fun willOpen(value: String): Boolean {
            queried += value
            return enabled && accepted
        }

        override fun open(value: String) {
            order += "open:$value"
        }
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
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("notti_inapp_url_test", Context.MODE_PRIVATE)
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
        coordinator.clickSink = null
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun visibleDisplay(): InAppDisplay {
        coordinator.onInitialized("app-fixture", "key-fixture", server.url("/").toString().trimEnd('/'))
        coordinator.session.markRegistered()
        coordinator.onSessionStarted()
        val display = presenter.presented.single()
        coordinator.onDisplayAttached(display.displayId, true)
        coordinator.onDisplayVisible(display.displayId)
        return display
    }

    private fun listener() {
        coordinator.clickSink =
            InAppClickSink {
                order += "listener"
                true
            }
    }

    private val warned: Boolean
        get() = logs.contains(NottiInAppCoordinator.NO_LISTENER_WARNING)

    @Test
    fun anAcceptedUrlOpensOnceAfterTheListenersAndTheClose() {
        listener()
        coordinator.urlOpener = RecordingOpener(accepted = true)
        val display = visibleDisplay()

        coordinator.onDisplayClicked(display.displayId, "b1", InAppAction("url", "https://example.invalid/x"))

        assertEquals(listOf("listener", "close", "open:https://example.invalid/x"), order)
        assertFalse(warned)
    }

    @Test
    fun anOpenedUrlWithNoListenerIsHandledAndNotWarned() {
        coordinator.urlOpener = RecordingOpener(accepted = true)
        val display = visibleDisplay()

        coordinator.onDisplayClicked(display.displayId, "b1", InAppAction("url", "https://example.invalid/x"))

        assertEquals(listOf("close", "open:https://example.invalid/x"), order)
        assertFalse(warned)
    }

    @Test
    fun aRefusedUrlIsNotOpenedAndWarnedWithNoListener() {
        coordinator.urlOpener = RecordingOpener(accepted = false)
        val display = visibleDisplay()

        coordinator.onDisplayClicked(display.displayId, "b1", InAppAction("url", "tel:+550000000000"))

        assertEquals(listOf("close"), order)
        assertTrue(warned)
    }

    @Test
    fun aDisabledOpenerOpensNothing() {
        listener()
        val opener = RecordingOpener(accepted = true).apply { enabled = false }
        coordinator.urlOpener = opener
        val display = visibleDisplay()

        coordinator.onDisplayClicked(display.displayId, "b1", InAppAction("url", "https://example.invalid/x"))

        assertEquals(listOf("listener", "close"), order)
        assertFalse(warned)
    }

    @Test
    fun aDeeplinkNeverReachesTheOpener() {
        val opener = RecordingOpener(accepted = true)
        coordinator.urlOpener = opener
        val display = visibleDisplay()

        coordinator.onDisplayClicked(display.displayId, "b1", InAppAction("deeplink", "https://example.invalid/x"))

        assertEquals(emptyList<String>(), opener.queried)
        assertEquals(listOf("close"), order)
        assertTrue(warned)
    }

    @Test
    fun aPushPermissionTapNeverReachesTheOpener() {
        val opener = RecordingOpener(accepted = true)
        coordinator.urlOpener = opener
        val display = visibleDisplay()

        coordinator.onDisplayClicked(display.displayId, "b1", InAppAction(InAppAction.PUSH_PERMISSION, ""))

        assertEquals(emptyList<String>(), opener.queried)
        assertFalse(warned)
    }

    @Test
    fun withNoOpenerAUrlBehavesAsInReactNative() {
        val display = visibleDisplay()

        coordinator.onDisplayClicked(display.displayId, "b1", InAppAction("url", "https://example.invalid/x"))

        assertEquals(listOf("close"), order)
        assertTrue(warned)
    }
}
