package com.zeeplabs.notti.example

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * In-memory event log shown on the screen: the last [MAX_LINES] lines, newest first. Lines are redacted by the
 * caller (EXR-03): event kinds, data keys, message and button ids, action types and which call ran; never a
 * typed value, client key, push token, data value or URL. Every line also goes to logcat, and nothing else
 * from the example does. Kept by the Application, so events before the screen exists are not lost.
 */
object ExampleEventLog {
    private const val MAX_LINES = 50
    private val main = Handler(Looper.getMainLooper())
    private val lines = ArrayDeque<String>()
    private var observer: ((List<String>) -> Unit)? = null

    fun add(line: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { add(line) }
            return
        }
        Log.i(ExampleApplication.TAG, line)
        lines.addFirst(line)
        while (lines.size > MAX_LINES) lines.removeLast()
        observer?.invoke(lines.toList())
    }

    /** One observer at a time (the screen); it gets the current lines at once. Main thread only. */
    fun observe(onChange: ((List<String>) -> Unit)?) {
        observer = onChange
        onChange?.invoke(lines.toList())
    }
}
