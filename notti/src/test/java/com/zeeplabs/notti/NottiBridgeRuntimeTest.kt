package com.zeeplabs.notti

import android.os.Bundle
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
import org.robolectric.annotation.Config
import java.lang.reflect.Modifier

/** [NottiBridge] gives the service's results (PUSH-13) and is Java-callable (PUSH-14). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiBridgeRuntimeTest {
    @Before
    @After
    fun reset() {
        NottiRuntime.resetForTest()
    }

    private fun message(vararg pairs: Pair<String, String>): RemoteMessage =
        RemoteMessage(Bundle().apply { pairs.forEach { (key, value) -> putString(key, value) } })

    @Test
    fun messageWithNoRuntimeIsANoOpThatCreatesNothing() {
        NottiBridge.onMessageReceived(message("notification_id" to "notification-fixture", "delivery_id" to "delivery-fixture"))

        assertNull(NottiRuntime.peek())
    }

    @Test
    fun tokenWithNoRuntimeIsANoOpThatCreatesNothing() {
        NottiBridge.onNewToken("token-fixture")

        assertNull(NottiRuntime.peek())
    }

    @Test
    fun messageWithARuntimeAndNoCoreMatchesTheService() {
        val runtime = NottiRuntime.get(RuntimeEnvironment.getApplication())
        val received = mutableListOf<ParsedNotification>()
        runtime.onNotificationReceived = { received += it }

        NottiBridge.onMessageReceived(
            message(
                "gcm.n.e" to "1",
                "gcm.n.title" to "Title fixture",
                "notification_id" to "notification-fixture",
                "delivery_id" to "delivery-fixture",
            ),
        )

        assertEquals(listOf("Title fixture"), received.map { it.title })
        val stored = runtime.eventStore.all().single()
        assertEquals("notification-fixture", stored.notificationId)
        assertEquals("delivery-fixture", stored.deliveryId)
        assertEquals(NottiEventType.RECEIVED, stored.type)
    }

    @Test
    fun messageWithoutBothIdsEnqueuesNothing() {
        val runtime = NottiRuntime.get(RuntimeEnvironment.getApplication())

        NottiBridge.onMessageReceived(message("notification_id" to "notification-fixture"))

        assertTrue(runtime.eventStore.all().isEmpty())
    }

    @Test
    fun methodsAreStaticForJavaCallers() {
        val onMessage = NottiBridge::class.java.getMethod("onMessageReceived", RemoteMessage::class.java)
        val onToken = NottiBridge::class.java.getMethod("onNewToken", String::class.java)

        assertTrue(Modifier.isStatic(onMessage.modifiers) && Modifier.isPublic(onMessage.modifiers))
        assertTrue(Modifier.isStatic(onToken.modifiers) && Modifier.isPublic(onToken.modifiers))
    }
}
