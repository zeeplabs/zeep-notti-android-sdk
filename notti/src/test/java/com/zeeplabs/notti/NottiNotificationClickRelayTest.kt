package com.zeeplabs.notti

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/** [NottiNotificationClickRelay] on its own (PUSH-10); RN covered it only through the module. */
class NottiNotificationClickRelayTest {
    private val first = ParsedNotification("Title fixture", "Body fixture", mapOf("key" to "fixture"))
    private val second = ParsedNotification("Second fixture", null, emptyMap())

    @Before
    @After
    fun reset() {
        NottiNotificationClickRelay.reset()
    }

    @Test
    fun emitWithNoEmitterBuffersOneClickTakenOnce() {
        NottiNotificationClickRelay.emit(first)

        assertSame(first, NottiNotificationClickRelay.takePending())
        assertNull(NottiNotificationClickRelay.takePending())
    }

    @Test
    fun onlyTheLastClickIsBuffered() {
        NottiNotificationClickRelay.emit(first)
        NottiNotificationClickRelay.emit(second)

        assertSame(second, NottiNotificationClickRelay.takePending())
        assertNull(NottiNotificationClickRelay.takePending())
    }

    @Test
    fun attachedEmitterReceivesClicksAndNothingIsBuffered() {
        val delivered = mutableListOf<ParsedNotification>()
        NottiNotificationClickRelay.attach { delivered += it }

        NottiNotificationClickRelay.emit(first)

        assertEquals(listOf(first), delivered)
        assertNull(NottiNotificationClickRelay.takePending())
    }

    @Test
    fun attachDoesNotReplayTheBufferedClick() {
        NottiNotificationClickRelay.emit(first)
        val delivered = mutableListOf<ParsedNotification>()

        NottiNotificationClickRelay.attach { delivered += it }

        assertEquals(emptyList<ParsedNotification>(), delivered)
        assertSame(first, NottiNotificationClickRelay.takePending())
    }

    @Test
    fun detachIsIdentityGuarded() {
        val oldDelivered = mutableListOf<ParsedNotification>()
        val newDelivered = mutableListOf<ParsedNotification>()
        val old: (ParsedNotification) -> Unit = { oldDelivered += it }
        val new: (ParsedNotification) -> Unit = { newDelivered += it }
        NottiNotificationClickRelay.attach(old)
        NottiNotificationClickRelay.attach(new)

        NottiNotificationClickRelay.detach(old)
        NottiNotificationClickRelay.emit(first)

        assertEquals(emptyList<ParsedNotification>(), oldDelivered)
        assertEquals(listOf(first), newDelivered)
    }

    @Test
    fun detachOfTheCurrentEmitterBuffersAgain() {
        val emitter: (ParsedNotification) -> Unit = { throw AssertionError("detached") }
        NottiNotificationClickRelay.attach(emitter)

        NottiNotificationClickRelay.detach(emitter)
        NottiNotificationClickRelay.emit(first)

        assertSame(first, NottiNotificationClickRelay.takePending())
    }
}
