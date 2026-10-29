package com.zeeplabs.notti

import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Level filtering and lazy messages of [NottiLogger] (CORE-15, CORE-16), with a capturing sink. */
class NottiLoggerTest {
    private val lines = mutableListOf<Pair<NottiLogger.Level, String>>()

    @Before
    fun setUp() {
        NottiLogger.level = NottiLogger.Level.ERROR
        NottiLogger.sink = { level, message -> lines += level to message }
    }

    @After
    fun tearDown() {
        NottiLogger.level = NottiLogger.Level.ERROR
        NottiLogger.sink = NottiLogger.defaultSink
    }

    private fun logAll() {
        NottiLogger.verbose("fixture-verbose")
        NottiLogger.debug("fixture-debug")
        NottiLogger.info("fixture-info")
        NottiLogger.warn("fixture-warn")
        NottiLogger.error("fixture-error")
    }

    @Test
    fun defaultLevelIsError() {
        assertEquals(NottiLogger.Level.ERROR, NottiLogger.level)
    }

    @Test
    fun errorLevelEmitsErrorOnly() {
        logAll()
        assertEquals(listOf(NottiLogger.Level.ERROR to "fixture-error"), lines)
    }

    @Test
    fun noneEmitsNothing() {
        NottiLogger.level = NottiLogger.Level.NONE
        logAll()
        assertTrue(lines.isEmpty())
    }

    @Test
    fun verboseEmitsEveryLevelInOrder() {
        NottiLogger.level = NottiLogger.Level.VERBOSE
        logAll()
        assertEquals(
            listOf(
                NottiLogger.Level.VERBOSE,
                NottiLogger.Level.DEBUG,
                NottiLogger.Level.INFO,
                NottiLogger.Level.WARN,
                NottiLogger.Level.ERROR,
            ),
            lines.map { it.first },
        )
    }

    @Test
    fun warnLevelEmitsWarnAndError() {
        NottiLogger.level = NottiLogger.Level.WARN
        logAll()
        assertEquals(listOf("fixture-warn", "fixture-error"), lines.map { it.second })
    }

    @Test
    fun lazyMessageNotEvaluatedBelowLevel() {
        var evaluated = 0
        NottiLogger.debug { "fixture-${++evaluated}" }
        NottiLogger.info { "fixture-${++evaluated}" }
        NottiLogger.warn { "fixture-${++evaluated}" }
        NottiLogger.verbose { "fixture-${++evaluated}" }
        assertEquals(0, evaluated)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun lazyMessageNotEvaluatedWhenNone() {
        NottiLogger.level = NottiLogger.Level.NONE
        var evaluated = false
        NottiLogger.error {
            evaluated = true
            "fixture"
        }
        assertFalse(evaluated)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun lazyMessageEvaluatedOnceAtLevel() {
        var evaluated = 0
        NottiLogger.error { "fixture-${++evaluated}" }
        assertEquals(1, evaluated)
        assertEquals(listOf(NottiLogger.Level.ERROR to "fixture-1"), lines)
    }

    @Test
    fun levelPrioritiesMatchAndroidLog() {
        assertEquals(Log.VERBOSE, NottiLogger.Level.VERBOSE.priority)
        assertEquals(Log.DEBUG, NottiLogger.Level.DEBUG.priority)
        assertEquals(Log.INFO, NottiLogger.Level.INFO.priority)
        assertEquals(Log.WARN, NottiLogger.Level.WARN.priority)
        assertEquals(Log.ERROR, NottiLogger.Level.ERROR.priority)
    }
}
