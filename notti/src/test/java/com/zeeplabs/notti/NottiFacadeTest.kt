package com.zeeplabs.notti

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.os.Looper
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.tasks.Tasks
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
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
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** The [Notti] facade: initialize, identity, device id, log level, hooks (API-01..05, API-13, API-15). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiFacadeTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()
    private val requests: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())
    private val executors = Collections.synchronizedList(mutableListOf<ExecutorService>())
    private val clientUrls = Collections.synchronizedList(mutableListOf<String>())
    private val logged = Collections.synchronizedList(mutableListOf<String>())
    private val processLifecycle: LifecycleRegistry
        get() = ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry

    @Before
    fun setUp() {
        NottiRuntime.resetForTest()
        Notti.resetForTest()
        NottiActivityLifecycleListener.resetRegistrationForTest()
        NottiLogger.sink = { _, message -> logged += message }
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val echo = JSONObject().put("id", "device-fixture").put("tags", JSONObject())
                    if (request.method == "PATCH") {
                        val sent = JSONObject(request.body.clone().readUtf8())
                        sent.keys().forEach { key -> if (key != "token") echo.put(key, sent.get(key)) }
                    }
                    return MockResponse().setResponseCode(200).setBody(echo.toString())
                }
            }
        server.start()
    }

    @After
    fun tearDown() {
        NottiLogger.sink = NottiLogger.defaultSink
        NottiLogger.level = NottiLogger.Level.ERROR
        processLifecycle.removeObserver(NottiForegroundObserver)
        NottiActivityLifecycleListener.resetRegistrationForTest()
        Notti.resetForTest()
        NottiRuntime.resetForTest()
        executors.forEach { it.shutdownNow() }
        server.shutdown()
    }

    private val mockUrl: String
        get() = server.url("/").toString().trimEnd('/')

    /**
     * Makes an isolated runtime the process runtime: own prefs, fixed token, and an API client that
     * records the base URL it was given and talks to the mock server.
     */
    private fun installRuntime(): NottiRuntime {
        val prefs = app.getSharedPreferences("notti_facade_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val runtime =
            NottiRuntime(
                context = app,
                prefs = prefs,
                providersFactory = { store, activity ->
                    NottiAndroidProviders(
                        context = app,
                        deviceStore = store,
                        currentActivity = activity,
                        geoExecutor = { it.run() },
                        firebaseToken = { Tasks.forResult("token-fixture") },
                    )
                },
                executorFactory = { Executors.newSingleThreadExecutor().also { executors += it } },
                apiClientFactory = { id, key, url ->
                    clientUrls += url
                    NottiApiClient(OkHttpClient(), mockUrl, id, key)
                },
            )
        NottiRuntime.setForTest(runtime)
        return runtime
    }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met in time" }
            Thread.sleep(20)
        }
    }

    private fun awaitIdle() {
        executors.filterNot { it.isShutdown }.forEach { it.submit {}.get(10, TimeUnit.SECONDS) }
    }

    private fun registrations(): List<RecordedRequest> =
        synchronized(requests) { requests.filter { it.method == "POST" && it.path!!.endsWith("/devices") } }

    private fun patches(): List<JSONObject> =
        synchronized(requests) { requests.filter { it.method == "PATCH" } }.map { JSONObject(it.body.clone().readUtf8()) }

    private fun connectivityCallbacks(): Int {
        val manager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return shadowOf(manager).networkCallbacks.size
    }

    private fun initializeAndAwaitRegistration(runtime: NottiRuntime) {
        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions(mockUrl))
        awaitUntil { runtime.deviceStore.getDeviceId() == "device-fixture" }
        awaitIdle()
    }

    @Test
    fun initializeUsesTheSaasBaseUrlWhenNoneIsGiven() {
        val runtime = installRuntime()

        Notti.initialize(app, "app-fixture", "key-fixture")

        awaitUntil { runtime.deviceStore.getDeviceId() == "device-fixture" }
        assertEquals(listOf("https://app.zeepnotti.app"), clientUrls.toList())
        assertEquals(1, registrations().size)
    }

    @Test
    fun sameConfigDoesNotRegisterAgainAndADifferentOneDoes() {
        val runtime = installRuntime()
        initializeAndAwaitRegistration(runtime)

        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions("$mockUrl/"))
        awaitIdle()
        assertEquals(1, registrations().size)

        Notti.initialize(app, "app-fixture", "key-other", NottiOptions(mockUrl))
        awaitUntil { registrations().size == 2 }
        awaitIdle()
        assertEquals(2, registrations().size)
    }

    @Test
    fun invalidInputNeitherThrowsNorRegistersAndKeepsTheConfig() {
        val runtime = installRuntime()
        initializeAndAwaitRegistration(runtime)

        Notti.initialize(app, " ", "key-sentinel", NottiOptions(mockUrl))
        Notti.initialize(app, "app-sentinel", "", NottiOptions(mockUrl))
        Notti.initialize(app, "app-sentinel", "key-sentinel", NottiOptions("not a url"))
        awaitIdle()

        assertEquals(1, registrations().size)
        assertFalse(logged.toString(), logged.any { it.contains("sentinel") })
        Notti.login("user-fixture")
        awaitUntil { patches().any { it.has("external_user_id") } }
        val login = synchronized(requests) { requests.last { it.method == "PATCH" } }
        assertTrue(login.path!!.startsWith("/v1/apps/app-fixture/devices/"))
    }

    @Test
    fun theFacadeReadsAndWritesNottiPrefs() {
        val golden = app.getSharedPreferences("notti_prefs", Context.MODE_PRIVATE)
        golden
            .edit()
            .clear()
            .putString("notti_device_id", "device-fixture-golden")
            .commit()
        NottiRuntime.get(app)

        assertEquals("device-fixture-golden", Notti.deviceId)
        Notti.logout()

        awaitUntil { golden.getBoolean("notti_pending_external_user_id_clear", false) }
    }

    @Test
    fun loginAndLogoutReachTheCore() {
        val runtime = installRuntime()
        initializeAndAwaitRegistration(runtime)

        Notti.login("user-fixture")
        awaitUntil { patches().any { it.optString("external_user_id") == "user-fixture" } }
        Notti.logout()

        awaitUntil { patches().any { it.has("external_user_id") && it.isNull("external_user_id") } }
    }

    /**
     * The core's rule, kept (RN parity): `login` before `initialize` is ignored with an error line,
     * while `logout` persists its clear and sends it at the first registration.
     */
    @Test
    fun beforeInitializeLoginIsIgnoredAndLogoutConvergesAtRegistration() {
        val runtime = installRuntime()

        Notti.login("user-sentinel")
        Notti.logout()
        awaitIdle()
        assertEquals(0, server.requestCount)
        assertTrue(logged.toString(), logged.contains("Notti.login: called before initialize - ignored"))

        initializeAndAwaitRegistration(runtime)
        awaitUntil { patches().any { it.has("external_user_id") && it.isNull("external_user_id") } }
        assertFalse(patches().any { it.optString("external_user_id") == "user-sentinel" })
        assertFalse(logged.any { it.contains("sentinel") })
    }

    @Test
    fun callsWithNoRuntimeAreDroppedWithAWarnNamingOnlyTheMethod() {
        Notti.logLevel = NottiLogLevel.WARN

        Notti.login("user-sentinel")
        Notti.logout()

        assertNull(Notti.deviceId)
        assertNull(NottiRuntime.peek())
        assertEquals(
            listOf(
                "Notti.login: called before initialize with no runtime - dropped",
                "Notti.logout: called before initialize with no runtime - dropped",
            ),
            logged.toList(),
        )
    }

    @Test
    fun deviceIdBeforeInitializeIsThePersistedIdOrNull() {
        val runtime = installRuntime()
        assertNull(Notti.deviceId)

        runtime.deviceStore.setDeviceId("device-fixture-stored")

        assertEquals("device-fixture-stored", Notti.deviceId)
    }

    @Test
    fun deviceIdListenersAreCalledOnceOnTheMainThreadPerChange() {
        val runtime = installRuntime()
        val calls = mutableListOf<String>()
        val threads = mutableSetOf<Thread>()
        Notti.addDeviceIdChangedListener {
            calls += "a:$it"
            threads += Thread.currentThread()
        }
        Notti.addDeviceIdChangedListener {
            calls += "b:$it"
            threads += Thread.currentThread()
        }

        initializeAndAwaitRegistration(runtime)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("a:device-fixture", "b:device-fixture"), calls)
        assertEquals(setOf(Looper.getMainLooper().thread), threads)

        // A re-registration answering the same id calls no listener.
        Notti.initialize(app, "app-fixture", "key-other", NottiOptions(mockUrl))
        awaitUntil { registrations().size == 2 }
        awaitIdle()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, calls.size)
    }

    @Test
    fun initializeFromABackgroundThreadWorksAndKeepsNoContext() {
        val runtime = installRuntime()
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        thread { Notti.initialize(activity, "app-fixture", "key-fixture", NottiOptions(mockUrl)) }.join()

        awaitUntil { runtime.deviceStore.getDeviceId() == "device-fixture" }
        assertEquals(1, registrations().size)
        val contextFields =
            Notti::class.java.declaredFields
                .filter { Modifier.isStatic(it.modifiers) }
                .onEach { it.isAccessible = true }
                .filter { it.get(null) is Context }
        assertEquals(emptyList<Any>(), contextFields)
    }

    @Test
    fun initializeWithoutTheProviderInstallsTheHooksOnce() {
        installRuntime()
        val callbacksBefore = connectivityCallbacks()

        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions(mockUrl))
        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions(mockUrl))

        assertEquals(callbacksBefore + 1, connectivityCallbacks())
    }

    @Test
    fun initializeAfterTheProviderInstallsNoHooks() {
        installRuntime()
        val callbacksBefore = connectivityCallbacks()
        Robolectric.buildContentProvider(NottiInitProvider::class.java).create()

        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions(mockUrl))

        assertEquals(callbacksBefore + 1, connectivityCallbacks())
    }

    @Test
    fun logLevelMapsToTheLoggerAndDefaultsToError() {
        assertEquals(NottiLogLevel.ERROR, Notti.logLevel)

        Notti.logLevel = NottiLogLevel.VERBOSE

        assertEquals(NottiLogger.Level.VERBOSE, NottiLogger.level)
        assertEquals(NottiLogLevel.VERBOSE, Notti.logLevel)
        Notti.logLevel = NottiLogLevel.NONE
        assertEquals(NottiLogger.Level.NONE, NottiLogger.level)
    }
}
