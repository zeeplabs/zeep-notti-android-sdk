package com.zeeplabs.notti

/** How much the SDK logs (to logcat, tag `Notti`). Lowest first; [NONE] silences it. Default [ERROR]. */
enum class NottiLogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    NONE,
}

internal fun NottiLogLevel.toLoggerLevel(): NottiLogger.Level =
    when (this) {
        NottiLogLevel.VERBOSE -> NottiLogger.Level.VERBOSE
        NottiLogLevel.DEBUG -> NottiLogger.Level.DEBUG
        NottiLogLevel.INFO -> NottiLogger.Level.INFO
        NottiLogLevel.WARN -> NottiLogger.Level.WARN
        NottiLogLevel.ERROR -> NottiLogger.Level.ERROR
        NottiLogLevel.NONE -> NottiLogger.Level.NONE
    }

internal fun NottiLogger.Level.toPublic(): NottiLogLevel =
    when (this) {
        NottiLogger.Level.VERBOSE -> NottiLogLevel.VERBOSE
        NottiLogger.Level.DEBUG -> NottiLogLevel.DEBUG
        NottiLogger.Level.INFO -> NottiLogLevel.INFO
        NottiLogger.Level.WARN -> NottiLogLevel.WARN
        NottiLogger.Level.ERROR -> NottiLogLevel.ERROR
        NottiLogger.Level.NONE -> NottiLogLevel.NONE
    }
