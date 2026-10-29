// Ported from react-native-notti v0.8.0 (291d5a7): android/src/main/java/com/notti/NottiNetworkObserver.kt
@file:Suppress(
    "MissingPermission",
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:no-empty-first-line-in-class-body",
    "ktlint:standard:trailing-comma-on-declaration-site",
)

package com.zeeplabs.notti

import android.net.ConnectivityManager
import android.net.Network

/**
 * Watches for the device regaining network connectivity and triggers an
 * offline-event flush the moment a usable connection appears (design.md
 * offline event queue). Events enqueued while offline ([NottiEventStore]) are
 * drained as soon as the network is back, instead of waiting for the next
 * launch, foreground, or registration - the Android mirror of iOS'
 * `NottiNetworkObserver`.
 *
 * Registered exactly once per process from [NottiInitProvider.onCreate]
 * (which runs before any Activity, on a cold start, long before the lazy
 * [NottiRuntime] exists), so the flush reaches the live core through
 * [NottiRuntime.core] and is a safe no-op until that core exists.
 *
 * Uses [ConnectivityManager.registerDefaultNetworkCallback], which only
 * reports the default network and only while it has the INTERNET capability -
 * so [NetworkCallback.onAvailable] is a sufficient "network is usable" signal
 * and no [ConnectivityManager.NetworkCapabilities] inspection is needed.
 *
 * `onNetworkAvailable` is injectable so tests can drive the observer without a
 * real [ConnectivityManager]; the production default hands the flush to the
 * core, which dispatches it onto its own executor (this callback can fire on a
 * binder thread, and the flush is blocking HTTP - see [NottiCore.onNetworkAvailable]).
 */
internal class NottiNetworkObserver(
    private val connectivityManager: ConnectivityManager,
    private val onNetworkAvailable: () -> Unit = {
        NottiRuntime.peek()?.core?.onNetworkAvailable()
        // In-App retries its own queue and a failed `eligible`, with or without a core.
        NottiRuntime.peek()?.inAppLifecycle?.onNetworkAvailable()
    }
) {

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onNetworkAvailable()
        }
    }

    /**
     * Starts monitoring. Registration is wrapped so a [ConnectivityManager] that
     * rejects the callback (missing permission, Robolectric-less stub) can never
     * take the host app down (spec SDK-03 crash safety) - the worst case is a
     * missing offline-event flush trigger, which the foreground observer already
     * covers.
     */
    fun register() {
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (t: Throwable) {
            NottiLogger.error("Notti: could not register the network observer - ${t.javaClass.simpleName}")
        }
    }
}
