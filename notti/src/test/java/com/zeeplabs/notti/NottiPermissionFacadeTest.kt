package com.zeeplabs.notti

import android.app.Application
import android.content.Context
import android.os.Looper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Collections
import kotlin.concurrent.thread

/** `Notti.Notifications.requestPermission`: one answer, on the main thread (API-09, design D4). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiPermissionFacadeTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val logged = Collections.synchronizedList(mutableListOf<String>())
    private val pendingPrompts = mutableListOf<(Boolean) -> Unit>()
    private var prompts = 0

    @Before
    fun setUp() {
        NottiRuntime.resetForTest()
        Notti.resetForTest()
        NottiLogger.sink = { _, message -> logged += message }
    }

    @After
    fun tearDown() {
        NottiLogger.sink = NottiLogger.defaultSink
        NottiLogger.level = NottiLogger.Level.ERROR
        Notti.resetForTest()
        NottiRuntime.resetForTest()
    }

    /** A runtime whose permission requester holds each prompt until the test answers it; no network. */
    private fun installRuntime(): NottiRuntime {
        val prefs = app.getSharedPreferences("notti_permission_facade_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return NottiRuntime(
            context = app,
            prefs = prefs,
            permissionRequester = { callback ->
                prompts++
                pendingPrompts += callback
            },
        ).also { NottiRuntime.setForTest(it) }
    }

    private fun initialize() {
        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions("https://push.example.invalid"))
    }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun theAnswerArrivesOnceOnTheMainThread() {
        installRuntime()
        initialize()
        val answers = mutableListOf<Boolean>()
        val threads = mutableSetOf<Thread>()

        thread {
            Notti.Notifications.requestPermission {
                answers += it
                threads += Thread.currentThread()
            }
        }.join()
        thread { pendingPrompts.single()(true) }.join()
        assertTrue("posted, not inline", answers.isEmpty())
        idleMain()

        assertEquals(listOf(true), answers)
        assertEquals(setOf(Looper.getMainLooper().thread), threads)
    }

    @Test
    fun beforeInitializeTheAnswerIsFalseWithNoDialog() {
        installRuntime()
        val answers = mutableListOf<Boolean>()

        Notti.Notifications.requestPermission { answers += it }
        assertTrue(answers.isEmpty())
        idleMain()

        assertEquals(listOf(false), answers)
        assertEquals(0, prompts)
    }

    @Test
    fun withNoRuntimeTheAnswerIsFalseAndAWarnIsLogged() {
        NottiLogger.level = NottiLogger.Level.WARN
        val answers = mutableListOf<Boolean>()

        Notti.Notifications.requestPermission { answers += it }
        idleMain()

        assertEquals(listOf(false), answers)
        assertEquals(listOf("Notti.Notifications.requestPermission: called before initialize with no runtime - dropped"), logged.toList())
    }

    @Test
    fun twoRequestsAreEachAnsweredOnce() {
        installRuntime()
        initialize()
        val first = mutableListOf<Boolean>()
        val second = mutableListOf<Boolean>()

        Notti.Notifications.requestPermission { first += it }
        Notti.Notifications.requestPermission { second += it }
        pendingPrompts.forEach { it(false) }
        idleMain()

        assertEquals(2, prompts)
        assertEquals(listOf(false), first)
        assertEquals(listOf(false), second)
    }
}
