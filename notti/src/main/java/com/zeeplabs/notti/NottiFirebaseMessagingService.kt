// Ported from react-native-notti v0.8.0 (291d5a7): android/src/main/java/com/notti/NottiFirebaseMessagingService.kt
@file:Suppress(
    "ktlint:standard:class-signature",
    "ktlint:standard:function-signature",
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:no-empty-first-line-in-class-body",
    "ktlint:standard:trailing-comma-on-call-site",
)

package com.zeeplabs.notti

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Pure, unit-testable parse of a [RemoteMessage] into the SDK's [NotificationPayload] shape. */
internal data class ParsedNotification(val title: String?, val body: String?, val data: Map<String, String>)

internal fun parseRemoteMessage(remoteMessage: RemoteMessage): ParsedNotification = ParsedNotification(
    title = remoteMessage.notification?.title,
    body = remoteMessage.notification?.body,
    data = remoteMessage.data
)

/**
 * Receives FCM token refresh and foreground data/notification messages
 * (design.md). Notti always sends a `notification` payload, so Android's
 * system tray auto-displays it while backgrounded/killed -
 * [onMessageReceived] only fires reliably in the foreground; background/
 * killed-state click detection is handled separately (T10, via the launch
 * Intent's extras), not here.
 */
internal class NottiFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        deliverNewToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        deliverRemoteMessage(NottiRuntime.get(applicationContext), remoteMessage)
    }
}

/** The token path of the service and [NottiBridge.onNewToken]: the live core, else dropped (RN). */
internal fun deliverNewToken(token: String) {
    NottiRuntime.peek()?.core?.onTokenRefreshed(token)
}

/** The message path of the service and [NottiBridge.onMessageReceived]. */
internal fun deliverRemoteMessage(runtime: NottiRuntime, remoteMessage: RemoteMessage) {
    runtime.onNotificationReceived?.invoke(parseRemoteMessage(remoteMessage))

    // Event reporting - the "received" detection site (T4). Fires in the
    // foreground only (the class doc above); background/killed-state clicks
    // are detected by the Activity-lifecycle hook instead. The event goes into
    // the process-wide companion store (this Service runs before the lazy
    // module exists on a cold start) and is opportunistically flushed if the
    // core is already live; otherwise it stays queued for the next
    // network-available / foreground / registration-success trigger. A message
    // without the SDK's `notification_id`/`delivery_id` keys is not an
    // event-reporting notification and is skipped silently.
    val data = parseRemoteMessage(remoteMessage).data
    val notificationId = data["notification_id"]
    val deliveryId = data["delivery_id"]
    if (!notificationId.isNullOrBlank() && !deliveryId.isNullOrBlank()) {
        runtime.enqueueEvent(notificationId, deliveryId, NottiEventType.RECEIVED)
    }
}
