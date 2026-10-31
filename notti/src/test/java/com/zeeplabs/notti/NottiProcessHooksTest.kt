package com.zeeplabs.notti

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.os.Looper
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

/** API-13: the process hooks install once, whether the provider or `Notti.initialize` runs first. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiProcessHooksTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val logged = mutableListOf<String>()
    private val processLifecycle: LifecycleRegistry
        get() = ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry

    @Before
    fun setUp() {
        NottiRuntime.resetForTest()
        NottiActivityLifecycleListener.resetRegistrationForTest()
        processLifecycle.removeObserver(NottiForegroundObserver)
        NottiLogger.sink = { _, message -> logged += message }
    }

    @After
    fun tearDown() {
        NottiProcessHooks.inAppInstaller = { NottiInAppSetup.install(it) }
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
    fun twoInstallsRegisterEachHookOnce() {
        val callbacksBefore = connectivityCallbacks()
        val observersBefore = processLifecycle.observerCount

        NottiProcessHooks.installOnce(app)
        val inApp = NottiInApp.get()
        NottiProcessHooks.installOnce(app)
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(NottiRuntime.peek())
        assertNotNull(inApp)
        assertSame(inApp, NottiInApp.get())
        assertEquals(callbacksBefore + 1, connectivityCallbacks())
        assertEquals(observersBefore + 1, processLifecycle.observerCount)
        assertEquals(emptyList<String>(), logged)
    }

    @Test
    fun installAfterTheProviderIsANoOp() {
        val callbacksBefore = connectivityCallbacks()
        val observersBefore = processLifecycle.observerCount

        Robolectric.buildContentProvider(NottiInitProvider::class.java).create()
        val inApp = NottiInApp.get()
        NottiProcessHooks.installOnce(app)
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(inApp)
        assertSame(inApp, NottiInApp.get())
        assertEquals(callbacksBefore + 1, connectivityCallbacks())
        assertEquals(observersBefore + 1, processLifecycle.observerCount)
        assertEquals(emptyList<String>(), logged)
    }

    @Test
    fun aFailingInAppSetupDoesNotBreakTheOtherHooks() {
        val callbacksBefore = connectivityCallbacks()
        val observersBefore = processLifecycle.observerCount
        NottiProcessHooks.inAppInstaller = { throw IllegalStateException("sentinel-detail") }

        NottiProcessHooks.installOnce(app)
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(NottiRuntime.peek())
        assertEquals(callbacksBefore + 1, connectivityCallbacks())
        assertEquals(observersBefore + 1, processLifecycle.observerCount)
        assertEquals(listOf("Notti: could not set up In-App messages - IllegalStateException"), logged)
        assertTrue(logged.none { it.contains("sentinel-detail") })
    }
}
