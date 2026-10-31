package com.zeeplabs.notti

/**
 * A tap on an In-App message: the [messageId] of the message, the [buttonId] tapped (`"image"` for the
 * tappable image; a bar body tap reports its first button) and that button's [action]. [action] is null
 * for a `push_permission` button, which the SDK runs itself, and for a button with no valid action.
 * Built only by the SDK.
 *
 * [toString] prints the [buttonId] and the action type only, never the [messageId] or a link.
 */
class NottiInAppClick internal constructor(
    val messageId: String,
    val buttonId: String,
    val action: NottiInAppAction?,
) {
    override fun equals(other: Any?): Boolean =
        other is NottiInAppClick && other.messageId == messageId && other.buttonId == buttonId && other.action == action

    override fun hashCode(): Int {
        var result = messageId.hashCode()
        result = 31 * result + buttonId.hashCode()
        result = 31 * result + action.hashCode()
        return result
    }

    override fun toString(): String = "NottiInAppClick(buttonId=$buttonId, actionType=${action?.type})"
}

internal fun InAppClick.toPublic(): NottiInAppClick = NottiInAppClick(messageId, buttonId, action?.toPublic())
