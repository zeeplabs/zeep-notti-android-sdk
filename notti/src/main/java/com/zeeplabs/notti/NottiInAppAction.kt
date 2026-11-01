package com.zeeplabs.notti

/**
 * The action of a tapped In-App button or image: a [Type.URL] link or a [Type.DEEPLINK] for the host to
 * handle. [value] is exactly what the server sent. Built only by the SDK.
 *
 * [toString] prints the [type] only, never the [value]: a link may carry personal data.
 *
 * [Type] may gain values in a minor release (AD-018): give a `when` over it an `else` branch.
 */
class NottiInAppAction internal constructor(
    val type: Type,
    val value: String,
) {
    enum class Type { URL, DEEPLINK }

    override fun equals(other: Any?): Boolean = other is NottiInAppAction && other.type == type && other.value == value

    override fun hashCode(): Int = 31 * type.hashCode() + value.hashCode()

    override fun toString(): String = "NottiInAppAction(type=$type)"
}

/** The public form of a parsed action; null for `push_permission`, which the SDK runs itself. */
internal fun InAppAction.toPublic(): NottiInAppAction? =
    when (type) {
        "url" -> NottiInAppAction(NottiInAppAction.Type.URL, value)
        "deeplink" -> NottiInAppAction(NottiInAppAction.Type.DEEPLINK, value)
        else -> null
    }
