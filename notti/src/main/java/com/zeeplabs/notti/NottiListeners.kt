package com.zeeplabs.notti

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/** Posts to the main thread; the handler is created on first use (API-11). */
internal object NottiMainThread {
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    fun post(block: Runnable) {
        handler.post(block)
    }
}

/**
 * The listeners of one public event (design D3). [dispatch] always posts one runnable to the main
 * thread, also when called on it, so delivery order equals emission order and a listener never runs
 * inside the SDK call that emitted. Each listener runs in its own `try`: one that throws is logged by
 * class and the others still run (API-10).
 *
 * A listener added during a delivery gets the next event, not the current one; a removed one is not
 * called again, also later in the current delivery. [onFirstAdded] and [onLastRemoved] run under the
 * lock, in order, on the 0 → 1 and 1 → 0 transitions.
 */
internal class NottiListeners<T>(
    private val name: String,
    private val onFirstAdded: () -> Unit = {},
    private val onLastRemoved: () -> Unit = {},
    private val post: (Runnable) -> Unit = NottiMainThread::post,
) {
    /** Cleared on removal, so a removed listener is neither called nor held. */
    private class Entry<T>(
        @Volatile var listener: ((T) -> Unit)?,
    )

    private val entries = CopyOnWriteArrayList<Entry<T>>()
    private val lock = Any()

    val size: Int
        get() = entries.size

    fun add(listener: (T) -> Unit): NottiSubscription {
        val entry = Entry(listener)
        synchronized(lock) {
            entries.add(entry)
            if (entries.size == 1) onFirstAdded()
        }
        return NottiSubscription { remove(entry) }
    }

    private fun remove(entry: Entry<T>) {
        entry.listener = null
        synchronized(lock) {
            if (entries.remove(entry) && entries.isEmpty()) onLastRemoved()
        }
    }

    fun dispatch(value: T) {
        val snapshot = entries.toList()
        if (snapshot.isEmpty()) return
        post(
            Runnable {
                for (entry in snapshot) {
                    val listener = entry.listener ?: continue
                    try {
                        listener(value)
                    } catch (t: Throwable) {
                        NottiLogger.error("Notti: $name listener failed - ${t.javaClass.simpleName}")
                    }
                }
            },
        )
    }
}
