package com.zeeplabs.notti

import android.os.Looper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** [NottiListeners]: fan-out, main-thread delivery, removal, isolation (API-10, API-11), synchronous delivery (In-App D2). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiListenersTest {
    private val logged = mutableListOf<String>()

    @Before
    fun setUp() {
        NottiLogger.sink = { _, message -> logged += message }
    }

    @After
    fun tearDown() {
        NottiLogger.sink = NottiLogger.defaultSink
    }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun everyListenerGetsEveryEventInOrderOnTheMainThread() {
        val listeners = NottiListeners<String>("test")
        val calls = mutableListOf<String>()
        val threads = mutableSetOf<Thread>()
        listeners.add {
            calls += "a:$it"
            threads += Thread.currentThread()
        }
        listeners.add {
            calls += "b:$it"
            threads += Thread.currentThread()
        }

        thread { listeners.dispatch("1") }.join()
        listeners.dispatch("2")
        assertTrue("delivery is posted, never inline", calls.isEmpty())
        idleMain()

        assertEquals(listOf("a:1", "b:1", "a:2", "b:2"), calls)
        assertEquals(setOf(Looper.getMainLooper().thread), threads)
    }

    @Test
    fun removeStopsDeliveryIsIdempotentAndDropsTheListener() {
        val listeners = NottiListeners<String>("test")
        val calls = mutableListOf<String>()
        val subscription = listeners.add { calls += it }

        subscription.remove()
        subscription.remove()
        listeners.dispatch("1")
        idleMain()

        assertTrue(calls.isEmpty())
        assertEquals(0, listeners.size)
        val held = NottiSubscription::class.java.getDeclaredField("onRemove").apply { isAccessible = true }
        assertNull((held.get(subscription) as AtomicReference<*>).get())
    }

    @Test
    fun addAndRemoveInsideACallbackApplyFromTheNextEvent() {
        val listeners = NottiListeners<String>("test")
        val calls = mutableListOf<String>()
        lateinit var self: NottiSubscription
        self =
            listeners.add {
                calls += "a:$it"
                self.remove()
                listeners.add { value -> calls += "b:$value" }
            }

        listeners.dispatch("1")
        idleMain()
        listeners.dispatch("2")
        idleMain()

        assertEquals(listOf("a:1", "b:2"), calls)
    }

    @Test
    fun aThrowingListenerIsLoggedByClassAndTheOthersStillRun() {
        val listeners = NottiListeners<String>("received")
        val calls = mutableListOf<String>()
        listeners.add { throw IllegalStateException("sentinel-message") }
        listeners.add { calls += it }

        listeners.dispatch("1")
        idleMain()

        assertEquals(listOf("1"), calls)
        assertEquals(listOf("Notti: received listener failed - IllegalStateException"), logged)
        assertFalse(logged.any { it.contains("sentinel") })
    }

    @Test
    fun hooksRunOnTheZeroToOneAndOneToZeroTransitions() {
        var first = 0
        var last = 0
        val listeners = NottiListeners<String>("test", onFirstAdded = { first++ }, onLastRemoved = { last++ })

        val a = listeners.add {}
        val b = listeners.add {}
        assertEquals(1, first)
        a.remove()
        assertEquals(0, last)
        b.remove()
        b.remove()
        assertEquals(1, last)
        listeners.add {}
        assertEquals(2, first)
    }

    @Test
    fun dispatchWithNoListenersPostsNothing() {
        var posted: Runnable? = null
        val listeners = NottiListeners<String>("test", post = { posted = it })

        listeners.dispatch("1")
        assertNull(posted)

        listeners.add {}
        listeners.dispatch("2")
        assertNotNull(posted)
    }

    @Test
    fun dispatchNowCallsEveryListenerOnTheCallingThreadAndPostsNothing() {
        var posted: Runnable? = null
        val listeners = NottiListeners<String>("in-app click", post = { posted = it })
        val calls = mutableListOf<String>()
        val threads = mutableSetOf<Thread>()
        listeners.add {
            calls += "a:$it"
            threads += Thread.currentThread()
        }
        listeners.add { throw IllegalStateException("sentinel-message") }
        listeners.add {
            calls += "c:$it"
            threads += Thread.currentThread()
        }

        var delivered = false
        val worker = thread { delivered = listeners.dispatchNow("1") }
        worker.join()

        assertTrue(delivered)
        assertEquals(listOf("a:1", "c:1"), calls)
        assertEquals(setOf(worker), threads)
        assertNull(posted)
        assertEquals(listOf("Notti: in-app click listener failed - IllegalStateException"), logged)
    }

    @Test
    fun dispatchNowReportsNoDeliveryWithNoLiveListener() {
        val listeners = NottiListeners<String>("in-app click")
        assertFalse(listeners.dispatchNow("1"))

        var calls = 0
        listeners.add { calls++ }.remove()

        assertFalse(listeners.dispatchNow("2"))
        assertEquals(0, calls)
    }
}
