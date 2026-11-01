package com.zeeplabs.notti

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.BadParcelableException
import android.os.Bundle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Collections

/** Tap detection never throws into the host's lifecycle callbacks (PRR-10, AD-019). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiTapDetectionGuardTest {
    private val logged = Collections.synchronizedList(mutableListOf<String>())

    /** An Intent whose extras cannot be unparcelled, as a crafted one sent to an exported Activity. */
    private class UnreadableIntent : Intent() {
        override fun getExtras(): Bundle? = throw BadParcelableException("fixture")
    }

    @Before
    fun setUp() {
        NottiNotificationClickRelay.reset()
        NottiRuntime.resetForTest()
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("notti_tap_guard_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        NottiRuntime.setForTest(NottiRuntime(app, prefs))
        NottiLogger.sink = { _, message -> logged += message }
    }

    @After
    fun tearDown() {
        NottiLogger.sink = NottiLogger.defaultSink
        NottiNotificationClickRelay.reset()
        NottiRuntime.resetForTest()
    }

    @Test
    fun anUnreadableIntentIsLoggedByClassAndDropped() {
        val listener = NottiActivityLifecycleListener()
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        activity.intent = UnreadableIntent()

        listener.onActivityCreated(activity, null)
        listener.onActivityResumed(activity)

        assertNull(NottiNotificationClickRelay.takePending())
        assertEquals(
            listOf(
                "Notti: could not read the launch Intent - BadParcelableException",
                "Notti: could not read the launch Intent - BadParcelableException",
            ),
            logged.toList(),
        )
    }
}
