// Ported from react-native-notti v0.8.0 (291d5a7): android/src/test/java/com/notti/NottiForegroundObserverTest.kt
@file:Suppress(
    "ktlint:standard:chain-method-continuation",
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:no-empty-first-line-in-class-body",
    "ktlint:standard:trailing-comma-on-call-site",
)

package com.zeeplabs.notti

import android.content.Context
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.concurrent.Executor

/**
 * Drives the real [NottiForegroundObserver] callbacks and its main-looper
 * heartbeat ticker (Robolectric paused looper) against the process-wide
 * [NottiRuntime.sessionGate], with the live core swapped in through
 * [NottiForegroundObserver.coreProvider].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiForegroundObserverTest {

    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle by lazy { LifecycleRegistry(this) }
    }
    private lateinit var store: NottiDeviceStore
    private lateinit var runtime: NottiRuntime
    private var now = 0L

    @Before
    fun setUp() {
        NottiRuntime.resetForTest()
        runtime = NottiRuntime.get(RuntimeEnvironment.getApplication())
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("notti_observer_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = NottiDeviceStore(prefs)
        now = 1_000L
    }

    @After
    fun tearDown() {
        NottiForegroundObserver.onStop(owner) // stop any ticker left running
        NottiForegroundObserver.coreProvider = { NottiRuntime.peek()?.core }
        NottiForegroundObserver.inAppProvider = { NottiRuntime.peek()?.inAppLifecycle }
        NottiRuntime.resetForTest()
    }

    private fun newCore(): NottiCore {
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("notti_observer_test", Context.MODE_PRIVATE)
        return NottiCore(
            deviceStore = store,
            eventStore = NottiEventStore(prefs),
            apiClientFactory = { _, _, _ -> throw AssertionError("no network in this test") },
            tokenProvider = { it(null) },
            permissionRequester = { it(false) },
            logger = { },
            executor = Executor { it.run() },
            clock = { now },
            sessionGate = runtime.sessionGate
        )
    }

    /** Records the lifecycle calls; the coordinator-backed version is re-added by in-app-messages. */
    private class RecordingInAppLifecycle : NottiInAppLifecycle {
        val calls = mutableListOf<String>()

        override fun onAppForegrounded() {
            calls += "foregrounded"
        }

        override fun onSessionEnded() {
            calls += "sessionEnded"
        }

        override fun onAppBackgrounded() {
            calls += "backgrounded"
        }

        override fun onNetworkAvailable() {
            calls += "network"
        }
    }

    @Test
    fun `onStop with no live core still ends the In-App session`() {
        val coordinator = RecordingInAppLifecycle()
        NottiForegroundObserver.inAppProvider = { coordinator }
        NottiForegroundObserver.coreProvider = { null }

        NottiForegroundObserver.onStop(owner)
        assertEquals(listOf("sessionEnded", "backgrounded"), coordinator.calls)
    }

    @Test
    fun `onStop with no live core still closes the process session gate`() {
        val coreA = newCore()
        NottiForegroundObserver.coreProvider = { coreA }
        NottiForegroundObserver.onStart(owner)
        assertTrue(runtime.sessionGate.isActive)

        // RN reload invalidated core A; core B does not exist yet.
        NottiForegroundObserver.coreProvider = { null }
        NottiForegroundObserver.onStop(owner)

        assertFalse(runtime.sessionGate.isActive)
    }

    @Test
    fun `heartbeat ticks every interval while started and stops after onStop`() {
        val core = newCore()
        NottiForegroundObserver.coreProvider = { core }
        NottiForegroundObserver.onStart(owner)
        assertEquals(1_000L, store.getSessionStartedAtMs())
        val looper = shadowOf(Looper.getMainLooper())

        now = 61_000L
        looper.idleFor(Duration.ofMillis(NottiCore.HEARTBEAT_INTERVAL_MS))
        assertEquals(61_000L, store.getLastForegroundAtMs())

        now = 121_000L
        looper.idleFor(Duration.ofMillis(NottiCore.HEARTBEAT_INTERVAL_MS))
        assertEquals(121_000L, store.getLastForegroundAtMs())

        now = 130_000L
        NottiForegroundObserver.onStop(owner)
        assertEquals(1, store.getSessionCount())
        assertEquals(129_000L, store.getSessionTimeMs())

        // No tick after background: nothing re-written.
        now = 999_000L
        looper.idleFor(Duration.ofMillis(NottiCore.HEARTBEAT_INTERVAL_MS * 3))
        assertNull(store.getLastForegroundAtMs())
        assertNull(store.getSessionStartedAtMs())
    }
}
