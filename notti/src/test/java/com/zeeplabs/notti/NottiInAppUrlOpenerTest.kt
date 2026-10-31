package com.zeeplabs.notti

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** INAPP-13..16, design D3: what the In-App URL opener accepts, how it opens and how it fails. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiInAppUrlOpenerTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val logged = mutableListOf<Pair<NottiLogger.Level, String>>()

    @Before
    fun setUp() {
        NottiLogger.level = NottiLogger.Level.VERBOSE
        NottiLogger.sink = { level, message -> logged += level to message }
    }

    @After
    fun tearDown() {
        NottiLogger.sink = NottiLogger.defaultSink
        NottiLogger.level = NottiLogger.Level.ERROR
        NottiInApp.resetForTests()
    }

    @Test
    fun acceptsHttpAndHttpsWithAHostOnly() {
        val accepted =
            listOf(
                "https://example.invalid",
                "http://example.invalid/path?q=1#frag",
                "HTTPS://Example.invalid/x",
                "HtTp://example.invalid:8080",
            )
        val refused =
            listOf(
                "",
                "tel:+550000000000",
                "mailto:fixture@example.invalid",
                "app://fixture",
                "javascript:alert(1)",
                "ftp://example.invalid",
                "intent://example.invalid#Intent;end",
                "https://",
                "https:///path",
                "http:example.invalid",
                "//example.invalid",
                "https://exa mple.invalid",
                " https://example.invalid",
                "https://example.invalid\n",
                "https://example.invalid\t",
                "https://example.invalid\u0000",
                "https://example.invalid ",
            )
        accepted.forEach { assertTrue(it, NottiInAppUrlOpener.accepts(it)) }
        refused.forEach { assertFalse(it, NottiInAppUrlOpener.accepts(it)) }
    }

    @Test
    fun willOpenLogsOneFixedDebugLineForARefusedValueAndNothingWhileDisabled() {
        val opener = NottiInAppUrlOpener(app) { null }

        assertTrue(opener.willOpen("https://example.invalid"))
        assertFalse(opener.willOpen("app://sentinel"))
        assertEquals(listOf(NottiLogger.Level.DEBUG to "Notti.inapp: url action not opened"), logged)

        logged.clear()
        opener.enabled = false
        assertFalse(opener.willOpen("https://example.invalid"))
        assertFalse(opener.willOpen("app://sentinel"))
        assertEquals(emptyList<Pair<NottiLogger.Level, String>>(), logged)
    }

    @Test
    fun opensFromTheResumedActivity() {
        val activity =
            Robolectric
                .buildActivity(Activity::class.java)
                .create()
                .resume()
                .get()
        val opener = NottiInAppUrlOpener(app) { activity }

        opener.open("HTTPS://Example.invalid/x?e=1")

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(Uri.parse("HTTPS://Example.invalid/x?e=1"), started.data)
        assertTrue(started.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertEquals(0, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertNull(shadowOf(app).nextStartedActivity)
        assertEquals(emptyList<Pair<NottiLogger.Level, String>>(), logged)
    }

    @Test
    fun opensFromTheApplicationInANewTaskWithNoActivity() {
        val opener = NottiInAppUrlOpener(app) { null }

        opener.open("https://example.invalid")

        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(Uri.parse("https://example.invalid"), started.data)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun aFailedOpenLogsOneFixedWarnLineWithoutTheUrl() {
        var attempts = 0
        val noBrowser =
            object : ContextWrapper(app) {
                override fun startActivity(intent: Intent) {
                    attempts++
                    throw ActivityNotFoundException("sentinel.example.invalid")
                }
            }
        val opener = NottiInAppUrlOpener(noBrowser) { null }

        opener.open("https://sentinel.example.invalid")

        assertEquals(1, attempts)
        assertEquals(listOf(NottiLogger.Level.WARN to "Notti.inapp: url open failed - ActivityNotFoundException"), logged)
        assertFalse(logged.any { it.second.contains("sentinel") })
    }

    @Test
    fun theSetupWiresTheOpenerIntoTheCoordinator() {
        val coordinator = NottiInAppSetup.install(app)

        assertTrue(coordinator.urlOpener is NottiInAppUrlOpener)
        assertTrue(coordinator.urlOpener!!.enabled)
    }
}
