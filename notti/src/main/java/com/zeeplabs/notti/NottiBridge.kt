// Ported from react-native-notti v0.8.0 (291d5a7): android/src/main/java/com/notti/NottiBridge.kt
package com.zeeplabs.notti

import com.google.firebase.messaging.RemoteMessage

/**
 * Public entry points for a host app that owns its own `FirebaseMessagingService`
 * and wants to forward FCM callbacks into Notti manually, instead of relying
 * on this library's own manifest-declared `NottiFirebaseMessagingService`.
 * Android only delivers an FCM message to one registered service per app, so
 * an app that already has its own service (for any reason -- another push
 * provider, a shared notification pipeline, etc.) cannot also register a
 * second one from this library.
 *
 * Mirrors iOS's `NottiBridge` (`ios/NottiPushDelegate.swift`), which already
 * exposes its callbacks as plain `public static` methods callable from any
 * host `AppDelegate` without Notti owning `UNUserNotificationCenter`'s
 * delegate slot. Both methods run the same code as the manifest-declared
 * service (`NottiRuntime`); with no Notti runtime or no live core yet they are
 * safe no-ops, as RN was with no module.
 */
object NottiBridge {
    /** Call from your own `FirebaseMessagingService.onMessageReceived`. */
    @JvmStatic
    fun onMessageReceived(remoteMessage: RemoteMessage) {
        NottiRuntime.peek()?.let { deliverRemoteMessage(it, remoteMessage) }
    }

    /** Call from your own `FirebaseMessagingService.onNewToken`. */
    @JvmStatic
    fun onNewToken(token: String) {
        deliverNewToken(token)
    }
}
