package com.zeeplabs.notti

/**
 * The host's In-App click listeners (design D2). Process-wide, so a listener added before `initialize` or
 * before the engine exists is kept. The coordinator's click sink calls [deliver] on the main thread and
 * needs the listeners to have run, and to know whether any did, before it closes the message: delivery is
 * synchronous ([NottiListeners.dispatchNow]), not posted.
 */
internal object NottiInAppClicks {
    val listeners = NottiListeners<NottiInAppClick>("in-app click")

    /** True when at least one listener received the click. */
    fun deliver(click: InAppClick): Boolean = listeners.dispatchNow(click.toPublic())
}
