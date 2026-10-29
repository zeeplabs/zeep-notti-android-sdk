package com.zeeplabs.notti

import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/** The default [NottiLogger] sink writes to logcat under the `Notti` tag (CORE-15). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiLoggerSinkTest {
    @Before
    fun setUp() {
        ShadowLog.clear()
        NottiLogger.level = NottiLogger.Level.ERROR
        NottiLogger.sink = NottiLogger.defaultSink
    }

    @After
    fun tearDown() {
        NottiLogger.level = NottiLogger.Level.ERROR
        NottiLogger.sink = NottiLogger.defaultSink
    }

    @Test
    fun defaultSinkWritesErrorWithNottiTag() {
        NottiLogger.error("fixture")

        val logs = ShadowLog.getLogsForTag("Notti")
        assertEquals(1, logs.size)
        assertEquals(Log.ERROR, logs[0].type)
        assertEquals("fixture", logs[0].msg)
    }

    @Test
    fun defaultSinkMapsLevelToPriority() {
        NottiLogger.level = NottiLogger.Level.VERBOSE
        NottiLogger.verbose("fixture-v")
        NottiLogger.debug("fixture-d")
        NottiLogger.info("fixture-i")
        NottiLogger.warn("fixture-w")

        val types = ShadowLog.getLogsForTag("Notti").map { it.type }
        assertEquals(listOf(Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN), types)
    }

    @Test
    fun defaultSinkSilentBelowLevel() {
        NottiLogger.warn("fixture")
        assertTrue(ShadowLog.getLogsForTag("Notti").isEmpty())
    }
}
