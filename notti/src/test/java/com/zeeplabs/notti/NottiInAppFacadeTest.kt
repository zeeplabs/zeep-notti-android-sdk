package com.zeeplabs.notti

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
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
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The In-App side of the [Notti] facade (design D4, D5; INAPP-09, INAPP-12, INAPP-17, INAPP-19): RN
 * `NottiModuleTest`'s In-App cases re-expressed on `Notti`, plus the URL option. The coordinator runs on
 * the caller's thread and never fetches, so only the core talks to the mock server.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiInAppFacadeTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()
    private val requests: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())
    private val executors = Collections.synchronizedList(mutableListOf<ExecutorService>())
    private val processLifecycle: LifecycleRegistry
        get() = ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry

    @Before
    fun setUp() {
        NottiRuntime.resetForTest()
        Notti.resetForTest()
        NottiActivityLifecycleListener.resetRegistrationForTest()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    return MockResponse().setResponseCode(200).setBody(
                        JSONObject().put("id", "device-fixture").put("tags", JSONObject()).toString(),
                    )
                }
            }
        server.start()
    }

    @After
    fun tearDown() {
        processLifecycle.removeObserver(NottiForegroundObserver)
        NottiActivityLifecycleListener.resetRegistrationForTest()
        Notti.resetForTest()
        NottiRuntime.resetForTest()
        executors.forEach { it.shutdownNow() }
        server.shutdown()
    }

    private val mockUrl: String
        get() = server.url("/").toString().trimEnd('/')

    /** An isolated process runtime on the mock server, as in `NottiFacadeTest`. */
    private fun installRuntime(): NottiRuntime {
        val prefs = app.getSharedPreferences("notti_inapp_facade_test", Context.MODE_PRIVATE)
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
                apiClientFactory = { id, key, _ -> NottiApiClient(OkHttpClient(), mockUrl, id, key) },
            )
        NottiRuntime.setForTest(runtime)
        return runtime
    }

    /** RN `installCoordinator`: runs on the caller's thread; [onPost] sees each hop to main. */
    private fun installCoordinator(onPost: () -> Unit = {}): NottiInAppCoordinator {
        val prefs = app.getSharedPreferences("notti_inapp_facade_events", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val coordinator =
            NottiInAppCoordinator(
                store = NottiInAppEventStore(prefs),
                clientFactory = { config -> NottiApiClient(OkHttpClient(), config.baseUrl, config.appId, config.clientKey) },
                identity = { null },
                executor =
                    object : InAppExecutor {
                        override fun execute(task: () -> Unit) = Unit

                        override fun schedule(
                            delayMs: Long,
                            task: () -> Unit,
                        ) = Unit
                    },
                postToMain = { task ->
                    onPost()
                    task()
                },
            )
        coordinator.urlOpener = NottiInAppUrlOpener(app) { null }
        return NottiInApp.install(coordinator)
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

    private fun registrations(): Int = synchronized(requests) { requests.count { it.method == "POST" && it.path!!.endsWith("/devices") } }

    @Test
    fun initializeHandsTheNormalizedConfigToInAppBeforeTheCoreIsCreated() {
        val runtime = installRuntime()
        val coreAtPost = mutableListOf<NottiCore?>()
        val coordinator = installCoordinator { coreAtPost += runtime.core }

        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions("$mockUrl/"))

        assertEquals(InAppConfig("app-fixture", "key-fixture", mockUrl), coordinator.session.config)
        assertEquals(listOf<NottiCore?>(null), coreAtPost)
        assertNotNull(runtime.core)
    }

    @Test
    fun theCoreOpensTheInAppSessionWhenItOpensAForegroundSession() {
        val runtime = installRuntime()
        val coordinator = installCoordinator()
        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions(mockUrl))

        requireNotNull(runtime.core).onAppForegrounded()

        assertTrue(coordinator.session.isOpen)
    }

    @Test
    fun initializeWithAnInvalidConfigLeavesInAppWithoutOne() {
        installRuntime()
        val coordinator = installCoordinator()

        Notti.initialize(app, "app-fixture", "", NottiOptions(mockUrl))
        Notti.initialize(app, " ", "key-fixture", NottiOptions(mockUrl))
        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions("not a url"))

        assertNull(coordinator.session.config)
    }

    @Test
    fun trackEventForwardsTheNameSoACallRightAfterInitializeIsKept() {
        installRuntime()
        val coordinator = installCoordinator()

        Notti.trackEvent("before-initialize")
        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions(mockUrl))
        thread { Notti.trackEvent("promo") }.join()
        Notti.trackEvent("")
        Notti.trackEvent("  ")

        assertEquals(listOf("promo"), coordinator.session.pending)
    }

    @Test
    fun trackEventWithNoInAppEngineIsANoOp() {
        Notti.trackEvent("promo")

        assertNull(NottiInApp.get())
    }

    @Test
    fun aClickListenerAddedBeforeInitializeGetsTheExactPayloadUntilRemoved() {
        val received = mutableListOf<NottiInAppClick>()
        val subscription = Notti.InAppMessages.addClickListener { received += it }
        installRuntime()
        installCoordinator()
        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions(mockUrl))
        // Reserved characters and escapes arrive byte for byte.
        val value = "https://shop.example.invalid/p?a=1&b=%20x#frag"

        assertTrue(NottiInAppClicks.deliver(InAppClick("msg-1", "b2", InAppAction("url", value))))
        subscription.remove()
        assertFalse(NottiInAppClicks.deliver(InAppClick("msg-2", "b1", null)))

        assertEquals(listOf(NottiInAppClick("msg-1", "b2", NottiInAppAction(NottiInAppAction.Type.URL, value))), received)
    }

    @Test
    fun aClickWithoutAUsableActionCarriesActionNull() {
        val received = mutableListOf<NottiInAppClick>()
        Notti.InAppMessages.addClickListener { received += it }
        // The parser turns an unknown action type into no action.
        val parsed =
            parseEligibleResponse(
                """{"messages":[{"id":"m1","format":"center","title":"t","body":"b",""" +
                    """"buttons":[{"id":"b1","label":"Go","action":{"type":"sms","value":"x"}}]}]}""",
            )!!.single()

        assertTrue(NottiInAppClicks.deliver(InAppClick("m1", "b1", parsed.buttons.single().action)))

        assertNull(received.single().action)
    }

    @Test
    fun reinitializingWithOnlyTheUrlOptionChangedAppliesItWithoutANewRegistration() {
        val runtime = installRuntime()
        val coordinator = installCoordinator()
        val opener = requireNotNull(coordinator.urlOpener)
        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions(mockUrl))
        awaitUntil { runtime.deviceStore.getDeviceId() == "device-fixture" }
        awaitIdle()
        assertTrue(opener.enabled)

        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions(mockUrl, opensInAppUrls = false))
        awaitIdle()
        assertFalse(opener.enabled)

        // An invalid call changes nothing; the next valid one applies its own value.
        Notti.initialize(app, "app-fixture", "", NottiOptions(mockUrl, opensInAppUrls = true))
        assertFalse(opener.enabled)
        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions(mockUrl))
        awaitIdle()
        assertTrue(opener.enabled)

        assertEquals(1, registrations())
    }

    @Test
    fun initializeWiresThePushPermissionPortOnEveryCall() {
        installRuntime()
        val coordinator = installCoordinator()

        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions(mockUrl))
        val first = coordinator.pushPermissionPort
        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions(mockUrl))

        assertTrue(first is NottiPushPermissionAction)
        assertNotSame(first, coordinator.pushPermissionPort)
    }

    @Test
    @Config(sdk = [33])
    fun thePushPermissionActionWithoutAnActivityShowsNoPromptAndLeavesTheAskedFlagUnset() {
        val runtime = installRuntime()
        val coordinator = installCoordinator()
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions(mockUrl))

        requireNotNull(coordinator.pushPermissionPort).run()
        awaitIdle()

        assertFalse(runtime.deviceStore.getPermissionRequested())
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun thePushPermissionActionOpensTheSettingsFromTheCurrentActivityOnceAsked() {
        val runtime = installRuntime()
        val coordinator = installCoordinator()
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        runtime.deviceStore.setPermissionRequested(true)
        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions(mockUrl))
        val activity =
            Robolectric
                .buildActivity(Activity::class.java)
                .create()
                .start()
                .resume()
                .get()

        requireNotNull(coordinator.pushPermissionPort).run()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, started.action)
        assertEquals(app.packageName, started.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }
}
