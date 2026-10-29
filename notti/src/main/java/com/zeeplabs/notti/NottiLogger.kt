package com.zeeplabs.notti

import android.util.Log

/**
 * The SDK's single log sink (CORE-15, CORE-16, AD-011). Every SDK log line goes through here, so the
 * host controls the volume with [level] (default [Level.ERROR]) and tests capture lines by swapping
 * [sink]. Never pass email, phone, push token, external user id, country, tag values or URLs.
 */
internal object NottiLogger {
    /** Severity, lowest first. [NONE] silences the SDK. */
    enum class Level(
        internal val priority: Int,
    ) {
        VERBOSE(Log.VERBOSE),
        DEBUG(Log.DEBUG),
        INFO(Log.INFO),
        WARN(Log.WARN),
        ERROR(Log.ERROR),
        NONE(Log.ASSERT),
    }

    const val TAG = "Notti"

    /** Writes to logcat under [TAG]. Declared before [sink], which starts with it. */
    internal val defaultSink: (Level, String) -> Unit = { lineLevel, message ->
        Log.println(lineLevel.priority, TAG, message)
    }

    /** Lines below this level are dropped before their message is built. */
    @Volatile
    var level: Level = Level.ERROR

    /** Where emitted lines go; defaults to logcat under [TAG]. */
    @Volatile
    var sink: (Level, String) -> Unit = defaultSink

    fun error(message: String) = log(Level.ERROR) { message }

    fun error(message: () -> String) = log(Level.ERROR, message)

    fun warn(message: String) = log(Level.WARN) { message }

    fun warn(message: () -> String) = log(Level.WARN, message)

    fun info(message: String) = log(Level.INFO) { message }

    fun info(message: () -> String) = log(Level.INFO, message)

    fun debug(message: String) = log(Level.DEBUG) { message }

    fun debug(message: () -> String) = log(Level.DEBUG, message)

    fun verbose(message: String) = log(Level.VERBOSE) { message }

    fun verbose(message: () -> String) = log(Level.VERBOSE, message)

    /** True when a line at [lineLevel] would be emitted. */
    fun isLoggable(lineLevel: Level): Boolean {
        val current = level
        return lineLevel != Level.NONE && current != Level.NONE && lineLevel >= current
    }

    private inline fun log(
        lineLevel: Level,
        message: () -> String,
    ) {
        if (isLoggable(lineLevel)) sink(lineLevel, message())
    }
}
