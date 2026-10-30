package com.zeeplabs.notti

import java.util.concurrent.atomic.AtomicReference

/**
 * Returned when a listener is added. [remove] stops delivery to that listener; calling it again does
 * nothing. Keep it for as long as the listener should stay registered.
 */
class NottiSubscription internal constructor(
    onRemove: () -> Unit,
) {
    /** Cleared on the first [remove], so a removed subscription holds no listener. */
    private val onRemove = AtomicReference(onRemove)

    /** Stops delivery to the listener this subscription was returned for. Idempotent, thread-safe. */
    fun remove() {
        onRemove.getAndSet(null)?.invoke()
    }
}
