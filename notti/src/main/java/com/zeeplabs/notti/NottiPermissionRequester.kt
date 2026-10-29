package com.zeeplabs.notti

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Asks for `POST_NOTIFICATIONS` from whatever Activity the host has resumed (PUSH-16..18). Replaces RN
 * `NottiModule.requestNativePermission`, which needed React Native's `PermissionAwareActivity`: the
 * dialog is hosted by the SDK's own [NottiPermissionActivity], so the host Activity's type does not
 * matter and a host recreated while the dialog is up does not lose the answer.
 *
 * Each request gets its own id in a process-wide map; [resolve] removes it, so every callback runs
 * exactly once (RN A5: concurrent requests never consume each other's result).
 */
internal class NottiPermissionRequester(
    private val context: Context,
    private val deviceStore: NottiDeviceStore,
    private val currentActivity: () -> Activity?,
    private val sdkInt: () -> Int = { Build.VERSION.SDK_INT },
) {
    fun request(callback: (Boolean) -> Unit) {
        // Below Android 13 there is no runtime permission to request (RN P2-AC4).
        if (sdkInt() < Build.VERSION_CODES.TIRAMISU) {
            callback(true)
            return
        }
        val activity = currentActivity()
        if (activity == null) {
            callback(false)
            return
        }
        // Set before the dialog, only when it is shown, as RN: tells "never asked" from "denied".
        deviceStore.setPermissionRequested(true)
        val id = nextId.getAndIncrement()
        pending[id] = callback
        try {
            activity.startActivity(
                Intent(context, NottiPermissionActivity::class.java)
                    .putExtra(EXTRA_REQUEST_ID, id)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        } catch (t: Throwable) {
            NottiLogger.error("Notti: could not show the notification permission dialog - ${t.javaClass.simpleName}")
            resolve(id, false)
        }
    }

    companion object {
        /** The request id extra of [NottiPermissionActivity]'s Intent. */
        const val EXTRA_REQUEST_ID = "com.zeeplabs.notti.extra.PERMISSION_REQUEST_ID"

        private val nextId = AtomicInteger(1)
        private val pending = ConcurrentHashMap<Int, (Boolean) -> Unit>()

        /** Runs and forgets the callback of [id]; a second call for the same id does nothing. */
        fun resolve(
            id: Int,
            granted: Boolean,
        ) {
            pending.remove(id)?.invoke(granted)
        }

        /** True while [id] waits for its answer. */
        fun isPending(id: Int): Boolean = pending.containsKey(id)

        /** Test-only: the map is process-wide state that outlives a single test case. */
        internal fun resetForTest() {
            pending.clear()
            nextId.set(1)
        }
    }
}
