package com.zeeplabs.notti

import android.app.Activity
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The current-Activity tracker (PUSH-19): last resumed, cleared on its own destroy, weakly held. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiCurrentActivityTest {
    private lateinit var runtime: NottiRuntime

    @Before
    fun setUp() {
        NottiRuntime.resetForTest()
        NottiActivityLifecycleListener.resetRegistrationForTest()
        runtime = NottiRuntime.get(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        NottiActivityLifecycleListener.resetRegistrationForTest()
        NottiRuntime.resetForTest()
    }

    @Test
    fun noActivityBeforeAnyResume() {
        assertNull(runtime.currentActivity())
    }

    @Test
    fun resumedActivityBecomesCurrentThroughTheListener() {
        NottiActivityLifecycleListener.registerOnce(RuntimeEnvironment.getApplication())

        val controller =
            Robolectric
                .buildActivity(Activity::class.java)
                .create()
                .start()
                .resume()

        assertSame(controller.get(), runtime.currentActivity())
    }

    @Test
    fun theSdkPermissionActivityNeverBecomesCurrent() {
        val listener = NottiActivityLifecycleListener()
        val host = Robolectric.buildActivity(Activity::class.java).get()
        val permission = Robolectric.buildActivity(NottiPermissionActivity::class.java).get()

        listener.onActivityResumed(host)
        listener.onActivityResumed(permission)
        listener.onActivityDestroyed(permission)

        assertSame(host, runtime.currentActivity())
    }

    @Test
    fun lastResumedWins() {
        NottiActivityLifecycleListener.registerOnce(RuntimeEnvironment.getApplication())

        Robolectric
            .buildActivity(Activity::class.java)
            .create()
            .start()
            .resume()
        val second =
            Robolectric
                .buildActivity(Activity::class.java)
                .create()
                .start()
                .resume()
                .get()

        assertSame(second, runtime.currentActivity())
    }

    @Test
    fun destroyingTheCurrentActivityClearsIt() {
        NottiActivityLifecycleListener.registerOnce(RuntimeEnvironment.getApplication())
        val controller =
            Robolectric
                .buildActivity(Activity::class.java)
                .create()
                .start()
                .resume()

        controller.pause().stop().destroy()

        assertNull(runtime.currentActivity())
    }

    @Test
    fun destroyingAnotherActivityKeepsTheCurrentOne() {
        NottiActivityLifecycleListener.registerOnce(RuntimeEnvironment.getApplication())
        val first =
            Robolectric
                .buildActivity(Activity::class.java)
                .create()
                .start()
                .resume()
        val second =
            Robolectric
                .buildActivity(Activity::class.java)
                .create()
                .start()
                .resume()

        first.pause().stop().destroy()

        assertSame(second.get(), runtime.currentActivity())
    }

    @Test
    fun destroyWithNoRuntimeDoesNotCreateOne() {
        NottiRuntime.resetForTest()

        NottiActivityLifecycleListener().onActivityDestroyed(Activity())

        assertNull(NottiRuntime.peek())
    }

    @Test
    fun theActivityIsHeldWeakly() {
        resumeThrowawayActivity()

        val deadline = System.currentTimeMillis() + 10_000
        while (runtime.currentActivity() != null && System.currentTimeMillis() < deadline) {
            System.gc()
            Thread.sleep(20)
        }

        assertNull(runtime.currentActivity())
    }

    /** Resumes an Activity no test variable keeps, so only the tracker could still reach it. */
    private fun resumeThrowawayActivity() {
        runtime.activityResumed(Activity())
    }
}
