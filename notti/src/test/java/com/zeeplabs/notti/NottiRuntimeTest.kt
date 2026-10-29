package com.zeeplabs.notti

import android.app.Activity
import android.app.Application
import android.content.Context
import com.google.android.gms.tasks.Tasks
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** [NottiRuntime]: lazy process instance, core lifecycle, cold-start events, prefs name (CORE-02..05, CORE-14). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiRuntimeTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()
    private val requests: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())
    private val executors = mutableListOf<ExecutorService>()

    @Before
    fun setUp() {
        NottiRuntime.resetForTest()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    // Like the backend: the device row, echoing every PATCHed field (the profile ack).
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
        NottiActivityLifecycleListener.resetRegistrationForTest()
        NottiPermissionRequester.resetForTest()
        NottiRuntime.resetForTest()
        executors.forEach { it.shutdownNow() }
        server.shutdown()
    }

    private val baseUrl: String
        get() = server.url("/").toString().trimEnd('/')

    /** A runtime on its own prefs file, a fixed FCM token and recorded executors. */
    private fun testRuntime(prefsName: String = "notti_runtime_test"): NottiRuntime {
        val prefs = app.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return NottiRuntime(
            context = app,
            prefs = prefs,
            providersFactory = { store, activity ->
                NottiAndroidProviders(
                    context = app,
                    deviceStore = store,
                    currentActivity = activity,
                    geoExecutor = { it.run() },
                    firebaseToken = { Tasks.forResult("fixture-token") },
                )
            },
            executorFactory = { Executors.newSingleThreadExecutor().also { executors += it } },
        )
    }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met in time" }
            Thread.sleep(20)
        }
    }

    @Test
    fun getIsLazySingleInstanceAcrossThreads() {
        assertNull(NottiRuntime.peek())
        val start = CountDownLatch(1)
        val seen = Collections.synchronizedSet(mutableSetOf<Int>())
        val pool = Executors.newFixedThreadPool(8)
        repeat(8) {
            pool.execute {
                start.await()
                seen += System.identityHashCode(NottiRuntime.get(app))
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        assertEquals(1, seen.size)
        assertSame(NottiRuntime.get(app), NottiRuntime.peek())
    }

    @Test
    fun productionPrefsFileIsNottiPrefs() {
        assertEquals("notti_prefs", NottiRuntime.PREFS_NAME)
        NottiRuntime.get(app).deviceStore.setDeviceId("device-fixture")
        val golden = app.getSharedPreferences("notti_prefs", Context.MODE_PRIVATE)
        assertEquals("device-fixture", golden.getString("notti_device_id", null))
    }

    @Test
    fun stopIsIdentityGuarded() {
        val runtime = testRuntime()
        val first = runtime.start("app-fixture", "key-fixture", baseUrl)
        val second = runtime.start("app-fixture", "key-fixture", baseUrl)

        runtime.stop(first)
        assertSame(second, runtime.core)

        runtime.stop(second)
        assertNull(runtime.core)
        assertTrue(executors[1].isShutdown)
    }

    @Test
    fun replacingCoreShutsTheOldExecutorDown() {
        val runtime = testRuntime()
        runtime.start("app-fixture", "key-fixture", baseUrl)
        assertFalse(executors[0].isShutdown)

        runtime.start("app-fixture", "key-fixture", baseUrl)

        assertTrue(executors[0].isShutdown)
        assertFalse(executors[1].isShutdown)
    }

    @Test
    fun enqueueWithoutCorePersists() {
        val runtime = testRuntime()
        runtime.enqueueEvent("notification-fixture", "delivery-fixture", NottiEventType.RECEIVED)
        assertEquals(1, runtime.eventStore.all().size)
    }

    @Test
    fun eventEnqueuedBeforeAnyCoreIsFlushedAfterStartAndRegistration() {
        val runtime = testRuntime()
        runtime.enqueueEvent("notification-fixture", "delivery-fixture", NottiEventType.RECEIVED)

        runtime.start("app-fixture", "key-fixture", baseUrl)

        awaitUntil { runtime.eventStore.all().isEmpty() }
        val paths = synchronized(requests) { requests.map { "${it.method} ${it.path}" } }
        val register = paths.indexOf("POST /v1/apps/app-fixture/devices")
        val report = paths.indexOf("POST /v1/apps/app-fixture/notifications/notification-fixture/events")
        assertTrue(paths.toString(), register >= 0)
        assertTrue(paths.toString(), report > register)
    }

    /**
     * As in RN, `sdk_version` is not in the `POST /devices` body: it is a profile field the core
     * PATCHes right after registration (`syncProfileFields`), from the value [NottiRuntime.start]
     * passes to `initialize`.
     */
    @Test
    fun registrationSyncSendsTheSdkVersionConstant() {
        val runtime = testRuntime()
        runtime.start("app-fixture", "key-fixture", baseUrl)

        fun sdkVersionBodies() =
            synchronized(requests) {
                requests
                    .filter { it.method == "PATCH" }
                    .map { JSONObject(it.body.clone().readUtf8()) }
                    .filter { it.has("sdk_version") }
            }
        awaitUntil { sdkVersionBodies().isNotEmpty() }
        assertEquals(NottiSdkVersion.CURRENT, sdkVersionBodies().single().getString("sdk_version"))
        assertEquals(NottiSdkVersion.CURRENT, runtime.deviceStore.getLastSyncedSdkVersion())
    }

    @Test
    fun observersReachTheRuntimeThroughPeek() {
        assertNull(NottiForegroundObserver.coreProvider())
        assertNull(NottiForegroundObserver.gateProvider())

        val runtime = NottiRuntime.get(app)
        assertSame(runtime.sessionGate, NottiForegroundObserver.gateProvider())
        assertNull(NottiForegroundObserver.inAppProvider())

        val core = runtime.start("app-fixture", "key-fixture", baseUrl)
        assertNotNull(core)
        assertSame(core, NottiForegroundObserver.coreProvider())
    }

    /**
     * PUSH-19, closes the core-port accepted exception: with the tracked Activity the rationale read is
     * real, so a never-requested, ungranted permission on API 34 is sent as `notDetermined` (RN parity)
     * instead of being omitted.
     */
    @Test
    fun permissionStatusIsSentOnApi34WithAResumedActivity() {
        val runtime = testRuntime()
        NottiRuntime.setForTest(runtime)
        NottiActivityLifecycleListener.registerOnce(app)
        val activity =
            Robolectric
                .buildActivity(Activity::class.java)
                .create()
                .start()
                .resume()
                .get()
        assertSame(activity, runtime.currentActivity())

        runtime.start("app-fixture", "key-fixture", baseUrl)

        fun statusBodies() =
            synchronized(requests) {
                requests
                    .filter { it.method == "PATCH" }
                    .map { JSONObject(it.body.clone().readUtf8()) }
                    .filter { it.has("permission_status") }
            }
        awaitUntil { statusBodies().isNotEmpty() }
        assertEquals("notDetermined", statusBodies().single().getString("permission_status"))
    }

    @Test
    fun defaultPermissionRequesterShowsTheSdkDialogFromTheCurrentActivity() {
        val runtime = testRuntime()
        val activity =
            Robolectric
                .buildActivity(Activity::class.java)
                .create()
                .start()
                .resume()
                .get()
        runtime.activityResumed(activity)
        val core = runtime.start("app-fixture", "key-fixture", baseUrl)

        core.requestPermission { }

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(NottiPermissionActivity::class.java.name, started?.component?.className)
        assertTrue(runtime.deviceStore.getPermissionRequested())
    }
}
