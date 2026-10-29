// Ported from react-native-notti v0.8.0 (291d5a7): android/src/main/java/com/notti/NottiInitProvider.kt lines 101-193 (NottiForegroundObserver only)
@file:Suppress(
    "ktlint:standard:chain-method-continuation",
    "ktlint:standard:function-signature",
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:trailing-comma-on-declaration-site",
)

package com.zeeplabs.notti

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * App-level foreground signal (whole process, not per-Activity). Registration
 * gives up after `NottiApiClient`'s 5-attempt backoff; without this the
 * device would stay unregistered until the process is killed. [NottiCore]
 * itself decides whether a retry is actually warranted. `onStop` is the
 * missing background hook (SEGTEL-06): [NottiCore.onAppBackgrounded] ends the
 * current session and enqueues its aggregate PATCH.
 *
 * While started it also ticks a foreground heartbeat every
 * [NottiCore.HEARTBEAT_INTERVAL_MS] on the main looper (SEGTEL-08: the "last
 * known foreground timestamp" used to close a session orphaned by a kill).
 * The ticker reads [NottiRuntime.core] on every tick, so it keeps working
 * when the lazy core is only created after `onStart` (the usual cold start).
 */
internal object NottiForegroundObserver : DefaultLifecycleObserver {
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** Where the callbacks/ticker find the live core. Test seam; production never reassigns it. */
    @Volatile
    internal var coreProvider: () -> NottiCore? = { null } // T8 points this at NottiRuntime.peek().

    /** Where the callbacks find the In-App coordinator. Test seam, like [coreProvider]. */
    @Volatile
    internal var inAppProvider: () -> NottiInAppLifecycle? = { null } // T8 points this at NottiRuntime.peek().

    /** Where [onStop] finds the process session gate; null skips the close (no runtime yet). */
    @Volatile
    internal var gateProvider: () -> NottiCore.SessionGate? = { null } // T8 points this at NottiRuntime.peek().

    private val heartbeat = object : Runnable {
        override fun run() {
            coreProvider()?.recordForegroundHeartbeat()
            mainHandler.postDelayed(this, NottiCore.HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        coreProvider()?.onAppForegrounded()
        inAppProvider()?.onAppForegrounded()
        mainHandler.removeCallbacks(heartbeat)
        mainHandler.postDelayed(heartbeat, NottiCore.HEARTBEAT_INTERVAL_MS)
    }

    override fun onStop(owner: LifecycleOwner) {
        mainHandler.removeCallbacks(heartbeat)
        gateProvider()?.let { handleProcessBackground(coreProvider(), it, inAppProvider()) }
    }

    /**
     * Closes the process-wide session gate even when no core is live: an RN
     * reload invalidates core A and the user can background before core B
     * exists. Left open, the gate would make core B's foreground a no-op (no
     * new session) and the old session would later be closed counting the
     * whole background interval. The orphaned session itself is closed by the
     * next session start at its last heartbeat (SEGTEL-08).
     */
    internal fun handleProcessBackground(
        core: NottiCore?,
        gate: NottiCore.SessionGate,
        inApp: NottiInAppLifecycle? = null
    ) {
        gate.close()
        core?.onAppBackgrounded()
        // Also with no core: the In-App session ends and the display clock pauses.
        inApp?.onSessionEnded()
        inApp?.onAppBackgrounded()
    }

    /**
     * Cold-start session fix: [NottiRuntime]'s core is lazy, so on a normal
     * launch `onStart` above fires while [NottiRuntime.core] is still null
     * and the launch session was never counted. Called right after a core is
     * created; if the process is already STARTED, the missed foreground is
     * replayed. Posted to the main looper (lifecycle state is main-thread
     * owned); a double signal (this + a late `onStart`) is deduped by the
     * core's session gate.
     */
    internal fun onCoreCreated(core: NottiCore) {
        try {
            mainHandler.post {
                syncWithCurrentState(core) {
                    ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                }
            }
        } catch (t: Throwable) {
            NottiLogger.error("Notti: could not sync the initial foreground state - ${t.javaClass.simpleName}")
        }
    }

    internal fun syncWithCurrentState(core: NottiCore, isProcessStarted: () -> Boolean) {
        try {
            if (isProcessStarted()) core.onAppForegrounded()
        } catch (t: Throwable) {
            NottiLogger.error("Notti: could not sync the initial foreground state - ${t.javaClass.simpleName}")
        }
    }
}
