package com.zeeplabs.notti

/**
 * A push notification as delivered to the host: the FCM notification [title] and [body], and the FCM
 * [data] map exactly as sent. Built only by the SDK.
 *
 * [toString] prints the [data] keys and whether a title and body are present, never their values:
 * a payload may carry personal data.
 */
class NottiNotification internal constructor(
    val title: String?,
    val body: String?,
    val data: Map<String, String>,
) {
    override fun equals(other: Any?): Boolean =
        other is NottiNotification && other.title == title && other.body == body && other.data == data

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + body.hashCode()
        result = 31 * result + data.hashCode()
        return result
    }

    override fun toString(): String = "NottiNotification(hasTitle=${title != null}, hasBody=${body != null}, dataKeys=${data.keys})"
}

internal fun ParsedNotification.toPublic(): NottiNotification = NottiNotification(title, body, data.toMap())
