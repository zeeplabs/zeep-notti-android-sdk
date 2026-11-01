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
            // Robolectric never moves ProcessLifecycleOwner to STARTED.
            isForeground = { true },
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

    private fun registrations(): Int =
        synchronized(requests) { requests.count { it.method == "POST" && it.path == "/v1/apps/app-fixture/devices" } }

    /** Waits until every queued task of the live executors ran (the core's work is serial on one). */
    private fun awaitIdle() {
        executors.filterNot { it.isShutdown }.forEach { it.submit {}.get(10, TimeUnit.SECONDS) }
    }

    @Test
    fun stopIsIdentityGuarded() {
        val runtime = testRuntime()
        val first = runtime.start("app-fixture", "key-fixture", baseUrl)
        runtime.stop(first)
        assertNull(runtime.core)
        assertTrue(executors[0].isShutdown)

        val second = runtime.start("app-fixture", "key-fixture", baseUrl)
        runtime.stop(first)
        assertSame(second, runtime.core)

        runtime.stop(second)
        assertNull(runtime.core)
        assertTrue(executors[1].isShutdown)
    }

    /** API-02 (AD-014): one core per runtime, re-initialized in place, as RN's lazy core. */
    @Test
    fun secondStartKeepsTheCoreAndRegistersOnlyForADifferentConfig() {
        val runtime = testRuntime()
        val first = runtime.start("app-fixture", "key-fixture", baseUrl)
        awaitUntil { runtime.deviceStore.getDeviceId() == "device-fixture" }
        awaitIdle()
        assertEquals(1, registrations())

        val same = runtime.start("app-fixture", "key-fixture", baseUrl)
        awaitIdle()
        assertSame(first, same)
        assertEquals(1, registrations())

        val other = runtime.start("app-fixture", "key-other", baseUrl)
        awaitUntil { registrations() == 2 }
        assertSame(first, other)
        assertEquals(1, executors.size)
        assertFalse(executors[0].isShutdown)
    }

    private fun inAppCoordinator(): NottiInAppCoordinator =
        NottiInAppCoordinator(
            store = NottiInAppEventStore(app.getSharedPreferences("notti_runtime_inapp_test", Context.MODE_PRIVATE)),
            clientFactory = { throw AssertionError("no network in this test") },
            identity = { null },
            executor =
                object : InAppExecutor {
                    override fun execute(task: () -> Unit) = Unit

                    override fun schedule(
                        delayMs: Long,
                        task: () -> Unit,
                    ) = Unit
                },
            postToMain = { it() },
        )

    /** D1: the core the runtime builds tells the installed In-App coordinator about each session start. */
    @Test
    fun theCoreSessionStartReachesTheInstalledInAppCoordinator() {
        val runtime = testRuntime()
        val coordinator = NottiInApp.install(inAppCoordinator())

        runtime.ensureCore().onAppForegrounded()
        awaitIdle()

        assertTrue(coordinator.session.isOpen)
    }

    /** D1: the runtime's In-App lifecycle forwards to the installed coordinator and is a no-op with none. */
    @Test
    fun theInAppLifecycleForwardsToTheInstalledCoordinator() {
        val runtime = testRuntime()
        val lifecycle = runtime.inAppLifecycle!!
        lifecycle.onAppForegrounded()
        lifecycle.onSessionEnded()
        lifecycle.onAppBackgrounded()
        lifecycle.onNetworkAvailable()

        val coordinator = NottiInApp.install(inAppCoordinator())
        coordinator.onSessionStarted()
        assertTrue(coordinator.session.isOpen)
        lifecycle.onSessionEnded()

        assertFalse(coordinator.session.isOpen)
    }

    @Test
    fun ensureCoreIsLazyAndDoesNotRegister() {
        val runtime = testRuntime()
        assertNull(runtime.core)

        val first = runtime.ensureCore()
        val second = runtime.ensureCore()
        awaitIdle()

        assertSame(first, second)
        assertSame(first, runtime.core)
        assertEquals(1, executors.size)
        assertEquals(0, server.requestCount)
    }

    /**
     * API-05: the core's device-id callback reaches the runtime slot. The slot is null in every other
     * test here, and registration goes on to the profile sync (`registrationSyncSendsTheSdkVersionConstant`).
     */
    @Test
    fun deviceIdChangeReachesTheRuntimeSlot() {
        val runtime = testRuntime()
        val changes = Collections.synchronizedList(mutableListOf<String>())
        runtime.onDeviceIdChanged = { changes += it }

        runtime.start("app-fixture", "key-fixture", baseUrl)

        awaitUntil { changes.isNotEmpty() }
        assertEquals(listOf("device-fixture"), changes.toList())
    }

    @Test
    fun concurrentStartsLeaveOneCoreAndOneExecutor() {
        val runtime = testRuntime()
        val go = CountDownLatch(1)
        val cores = Collections.synchronizedSet(mutableSetOf<Int>())
        val pool = Executors.newFixedThreadPool(8)
        repeat(8) {
            pool.execute {
                go.await()
                cores += System.identityHashCode(runtime.start("app-fixture", "key-fixture", baseUrl))
            }
        }
        go.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))

        awaitUntil { runtime.deviceStore.getDeviceId() == "device-fixture" }
        awaitIdle()
        assertEquals(1, cores.size)
        assertEquals(1, executors.size)
        assertEquals(1, registrations())
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
        // The core records the ack only after the 2xx reaches it, which can be after the server saw the PATCH.
        awaitUntil { runtime.deviceStore.getLastSyncedSdkVersion() != null }
        assertEquals(NottiSdkVersion.CURRENT, runtime.deviceStore.getLastSyncedSdkVersion())
    }

    @Test
    fun observersReachTheRuntimeThroughPeek() {
        assertNull(NottiForegroundObserver.coreProvider())
        assertNull(NottiForegroundObserver.gateProvider())

        val runtime = NottiRuntime.get(app)
        assertSame(runtime.sessionGate, NottiForegroundObserver.gateProvider())
        assertSame(NottiInApp.lifecycle, NottiForegroundObserver.inAppProvider())

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
