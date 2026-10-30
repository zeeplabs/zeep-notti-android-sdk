package com.zeeplabs.notti

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Looper
import com.google.firebase.messaging.RemoteMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** `Notti.Notifications` received and clicked listeners and the initial click (API-10..12). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiNotificationsFacadeTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        NottiRuntime.resetForTest()
        Notti.resetForTest()
        NottiNotificationClickRelay.reset()
        NottiLogger.sink = { _, _ -> }
    }

    @After
    fun tearDown() {
        NottiLogger.sink = NottiLogger.defaultSink
        Notti.resetForTest()
        NottiNotificationClickRelay.reset()
        NottiRuntime.resetForTest()
    }

    /** A runtime on its own prefs; the production token provider fails (no FirebaseApp), so no network. */
    private fun installRuntime(): NottiRuntime {
        val prefs = app.getSharedPreferences("notti_notifications_facade_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return NottiRuntime(app, prefs).also { NottiRuntime.setForTest(it) }
    }

    private fun message(vararg pairs: Pair<String, String>): RemoteMessage =
        RemoteMessage(Bundle().apply { pairs.forEach { (key, value) -> putString(key, value) } })

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private val tap = ParsedNotification("Fixture title", "Fixture body", mapOf("notification_id" to "notification-fixture"))

    @Test
    fun everyReceivedListenerGetsThePayloadUntilRemoved() {
        val runtime = installRuntime()
        val a = mutableListOf<NottiNotification>()
        val b = mutableListOf<NottiNotification>()
        val subscriptionA = Notti.Notifications.addReceivedListener { a += it }
        Notti.Notifications.addReceivedListener { b += it }

        deliverRemoteMessage(
            runtime,
            message("gcm.n.e" to "1", "gcm.n.title" to "Fixture title", "gcm.n.body" to "Fixture body", "k" to "fixture"),
        )
        idleMain()

        val expected = NottiNotification("Fixture title", "Fixture body", mapOf("k" to "fixture"))
        assertEquals(listOf(expected), a)
        assertEquals(listOf(expected), b)

        subscriptionA.remove()
        deliverRemoteMessage(runtime, message("k" to "fixture-2"))
        idleMain()
        assertEquals(1, a.size)
        assertEquals(mapOf("k" to "fixture-2"), b.last().data)
    }

    @Test
    fun aReceivedListenerAddedBeforeAnyRuntimeWorksOnceEventsFlow() {
        val received = mutableListOf<NottiNotification>()
        Notti.Notifications.addReceivedListener { received += it }
        assertNull(NottiRuntime.peek())

        val runtime = installRuntime()
        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions("https://push.example.invalid"))
        deliverRemoteMessage(runtime, message("k" to "fixture"))
        idleMain()

        assertEquals(listOf(mapOf("k" to "fixture")), received.map { it.data })
    }

    @Test
    fun withAClickedListenerTheTapIsDeliveredNotKept() {
        val clicked = mutableListOf<NottiNotification>()
        Notti.Notifications.addClickedListener { clicked += it }

        NottiNotificationClickRelay.emit(tap)
        idleMain()

        assertEquals(listOf(tap.toPublic()), clicked)
        assertNull(Notti.Notifications.takeInitialClick())
    }

    @Test
    fun afterTheLastClickedListenerIsRemovedTheTapIsKeptAndTakenOnce() {
        val clicked = mutableListOf<NottiNotification>()
        val a = Notti.Notifications.addClickedListener { clicked += it }
        val b = Notti.Notifications.addClickedListener { clicked += it }
        a.remove()
        b.remove()

        NottiNotificationClickRelay.emit(tap)
        idleMain()

        assertTrue(clicked.isEmpty())
        assertEquals(tap.toPublic(), Notti.Notifications.takeInitialClick())
        assertNull(Notti.Notifications.takeInitialClick())
    }

    @Test
    fun aTapBeforeAnyClickedListenerIsNotReplayedToALaterOne() {
        NottiNotificationClickRelay.emit(tap)
        val clicked = mutableListOf<NottiNotification>()
        Notti.Notifications.addClickedListener { clicked += it }
        idleMain()

        assertTrue(clicked.isEmpty())
        assertEquals(tap.toPublic(), Notti.Notifications.takeInitialClick())
    }
}
