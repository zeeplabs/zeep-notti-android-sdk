package com.zeeplabs.notti

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ProviderInfo
import android.net.ConnectivityManager
import android.os.Looper
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The process-start hook (PUSH-05, PUSH-06): registrations, the main-looper post, no network, no crash. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiInitProviderTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()
    private val logged = mutableListOf<String>()
    private val processLifecycle: LifecycleRegistry
        get() = ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry

    @Before
    fun setUp() {
        NottiRuntime.resetForTest()
        NottiActivityLifecycleListener.resetRegistrationForTest()
        processLifecycle.removeObserver(NottiForegroundObserver)
        NottiLogger.sink = { _, message -> logged += message }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        NottiLogger.sink = NottiLogger.defaultSink
        processLifecycle.removeObserver(NottiForegroundObserver)
        NottiActivityLifecycleListener.resetRegistrationForTest()
        NottiRuntime.resetForTest()
    }

    private fun connectivityCallbacks(): Int {
        val manager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return shadowOf(manager).networkCallbacks.size
    }

    @Test
    fun createsTheRuntimeAndRegistersTheHooksWithNoNetworkRequest() {
        val callbacksBefore = connectivityCallbacks()
        val observersBefore = processLifecycle.observerCount

        val provider = Robolectric.buildContentProvider(NottiInitProvider::class.java).create().get()

        assertNotNull(provider)
        assertNotNull(NottiRuntime.peek())
        assertEquals(callbacksBefore + 1, connectivityCallbacks())
        // The foreground observer is added only after the main-looper post.
        assertEquals(observersBefore, processLifecycle.observerCount)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(observersBefore + 1, processLifecycle.observerCount)
        assertEquals(0, server.requestCount)
        assertEquals(emptyList<String>(), logged)
    }

    @Test
    fun registersTheLifecycleListenerOnce() {
        Robolectric.buildContentProvider(NottiInitProvider::class.java).create()
        NottiActivityLifecycleListener.registerOnce(app)

        val activity =
            Robolectric
                .buildActivity(Activity::class.java)
                .create()
                .start()
                .resume()
                .get()

        assertSame(activity, NottiRuntime.peek()?.currentActivity?.invoke())
    }

    @Test
    fun aThrowingStepDoesNotStopTheOthersNorCrash() {
        val broken = BrokenApplication()
        val context =
            object : ContextWrapper(app) {
                override fun getApplicationContext(): Context = broken
            }
        val observersBefore = processLifecycle.observerCount

        // attachInfo runs onCreate: listener registration, runtime (no base context: prefs throw) and
        // the network observer (getSystemService throws) all fail; the foreground post still runs.
        NottiInitProvider().attachInfo(context, ProviderInfo())
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(NottiRuntime.peek())
        assertEquals(observersBefore + 1, processLifecycle.observerCount)
        assertEquals(3, logged.size)
        assertTrue(logged.toString(), logged.all { it.startsWith("Notti: could not ") })
    }

    @Test
    fun noApplicationContextReturnsFalseAndLogs() {
        val context =
            object : ContextWrapper(app) {
                override fun getApplicationContext(): Context? = null
            }
        val provider = NottiInitProvider()

        provider.attachInfo(context, ProviderInfo())

        assertNull(NottiRuntime.peek())
        assertEquals(1, logged.size)
    }

    /** An Application with no base context whose lifecycle registration throws. */
    private class BrokenApplication : Application() {
        override fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks?): Unit =
            throw IllegalStateException("fixture")
    }
}
