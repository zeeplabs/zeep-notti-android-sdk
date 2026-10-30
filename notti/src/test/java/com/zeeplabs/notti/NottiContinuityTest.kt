package com.zeeplabs.notti

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.tasks.Tasks
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Storage continuity with react-native-notti 0.8.0 (CORE-12..14). Each test writes an RN 0.8.0
 * `notti_prefs` fixture with the exact key names and `SharedPreferences` types RN writes
 * (`traceability.md` "Storage contract"; synthetic values), then starts a runtime on that file.
 *
 * RN 0.8.0 posts `POST /devices` (an upsert keyed by the token) on every `initialize`, also for a
 * registered device with an unchanged token, so "no new registration unless RN would" (spec
 * P1-AC2) means: exactly one `POST /devices` carrying the stored token, the same device id kept,
 * no device-id change callback and no profile-ack reset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiContinuityTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()
    private val requests: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())
    private val deviceIdChanges: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private lateinit var prefs: SharedPreferences

    /** The device id the fake backend answers registration with. */
    @Volatile
    private var serverDeviceId = DEVICE_ID

    @Before
    fun setUp() {
        NottiRuntime.resetForTest()
        prefs = app.getSharedPreferences(NottiRuntime.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val tags = JSONObject().put("plan", "fixture-plan").put("tier", "fixture-tier")
                    val body = JSONObject().put("id", serverDeviceId).put("tags", tags)
                    if (request.method == "PATCH") {
                        val sent = JSONObject(request.body.clone().readUtf8())
                        sent.keys().forEach { key -> if (key != "token") body.put(key, sent.get(key)) }
                    }
                    return MockResponse().setResponseCode(200).setBody(body.toString())
                }
            }
        server.start()
    }

    @After
    fun tearDown() {
        NottiRuntime.resetForTest()
        server.shutdown()
    }

    /** RN 0.8.0 state of a registered install, written with RN's own types. */
    private fun writeRegisteredFixture() {
        val pending =
            JSONArray().put(
                JSONObject()
                    .put("id", "local-fixture-1")
                    .put("notificationId", "notification-fixture")
                    .put("deliveryId", "delivery-fixture")
                    .put("type", "received")
                    .put("createdAtMs", 1_500L),
            )
        prefs
            .edit()
            .putString("notti_device_id", DEVICE_ID)
            .putString("notti_last_token", TOKEN)
            .putBoolean("notti_subscribed", true)
            .putString("notti_tag_plan", "fixture-plan")
            .putString("notti_tag_tier", "fixture-tier")
            .putLong("notti_first_session_at_ms", 1_000L)
            .putLong("notti_last_session_at_ms", 2_000L)
            .putInt("notti_session_count", 3)
            .putLong("notti_session_time_ms", 45_000L)
            .putLong("notti_session_started_at_ms", -1L)
            .putLong("notti_last_foreground_at_ms", -1L)
            .putBoolean("notti_location_sharing_enabled", false)
            .putBoolean("notti_pending_country_clear", false)
            .putBoolean("notti_pending_external_user_id_clear", true)
            .putString("notti_app_version", "1.0.0-fixture")
            .putString("notti_last_synced_device_os", "os-fixture")
            .putString("notti_last_synced_device_model", "model-fixture")
            .putString("notti_last_synced_sdk_version", NottiSdkVersion.CURRENT)
            .putString("notti_last_synced_timezone_id", "Etc/UTC")
            .putString("notti_last_synced_language", "en")
            .putString("notti_last_synced_permission_status", "granted")
            .putLong("notti_last_unsubscribed_at_ms", -1L)
            .putString("notti_email", "fixture@example.invalid")
            .putString("notti_last_synced_email", "fixture@example.invalid")
            .putBoolean("notti_permission_requested", true)
            .putLong("notti_pending_unsubscribe_at_ms", -1L)
            .putLong("notti_pending_permission_unsubscribe_at_ms", -1L)
            .putBoolean("notti_profile_field_ack_migrated", true)
            .putString("notti_pending_events", pending.toString())
            .commit()
    }

    private fun runtime(fcmToken: String): NottiRuntime =
        NottiRuntime(
            context = app,
            prefs = prefs,
            providersFactory = { store, activity ->
                NottiAndroidProviders(
                    context = app,
                    deviceStore = store,
                    currentActivity = activity,
                    geoExecutor = { it.run() },
                    firebaseToken = { Tasks.forResult(fcmToken) },
                )
            },
            executorFactory = { Executors.newSingleThreadExecutor() },
        )

    private fun start(runtime: NottiRuntime) {
        runtime.onDeviceIdChanged = { deviceIdChanges += it }
        runtime.start(
            appId = "app-fixture",
            clientKey = "key-fixture",
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
    }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met in time" }
            Thread.sleep(20)
        }
    }

    private fun snapshot(): List<RecordedRequest> = synchronized(requests) { requests.toList() }

    private fun registrations() = snapshot().filter { it.method == "POST" && it.path == "/v1/apps/app-fixture/devices" }

    private fun patches() = snapshot().filter { it.method == "PATCH" }.map { JSONObject(it.body.clone().readUtf8()) }

    @Test
    fun registeredFixtureReadsBackWithRnTypes() {
        writeRegisteredFixture()
        val runtime = runtime(TOKEN)
        val store = runtime.deviceStore

        assertEquals(DEVICE_ID, store.getDeviceId())
        assertEquals(TOKEN, store.getLastToken())
        assertEquals(mapOf("plan" to "fixture-plan", "tier" to "fixture-tier"), store.getTags())
        assertEquals(1_000L, store.getFirstSessionAtMs())
        assertEquals(2_000L, store.getLastSessionAtMs())
        assertEquals(3, store.getSessionCount())
        assertEquals(45_000L, store.getSessionTimeMs())
        assertNull(store.getSessionStartedAtMs())
        assertEquals("1.0.0-fixture", store.getAppVersion())
        assertEquals("os-fixture", store.getLastSyncedDeviceOs())
        assertEquals(NottiSdkVersion.CURRENT, store.getLastSyncedSdkVersion())
        assertEquals("granted", store.getLastSyncedPermissionStatus())
        assertEquals("fixture@example.invalid", store.getLastSyncedEmail())
        assertTrue(store.getPendingExternalUserIdClear())
        assertTrue(store.getPermissionRequested())
        assertEquals(true, store.getSubscribedOrNull())
        val pending = runtime.eventStore.all().single()
        assertEquals("local-fixture-1", pending.id)
        assertEquals("notification-fixture", pending.notificationId)
        assertEquals("delivery-fixture", pending.deliveryId)
        assertEquals(NottiEventType.RECEIVED, pending.type)
        assertEquals(1_500L, pending.createdAtMs)
    }

    @Test
    fun registeredFixtureKeepsDeviceIdAndDoesNotRegisterAsNew() {
        writeRegisteredFixture()
        val runtime = runtime(TOKEN)

        start(runtime)
        awaitUntil { runtime.eventStore.all().isEmpty() && !runtime.deviceStore.getPendingExternalUserIdClear() }

        // RN's upsert with the stored token: one POST, nothing else registers.
        val register = registrations().single()
        assertEquals(TOKEN, JSONObject(register.body.readUtf8()).getString("token"))
        assertEquals(DEVICE_ID, runtime.deviceStore.getDeviceId())
        assertTrue(deviceIdChanges.isEmpty())
        // Acks survive: the acked sdk_version is not re-sent.
        assertFalse(patches().any { it.has("sdk_version") })
        assertEquals(NottiSdkVersion.CURRENT, runtime.deviceStore.getLastSyncedSdkVersion())
        // Pending logout clear is honored, then dropped.
        assertTrue(patches().any { it.has("external_user_id") && it.isNull("external_user_id") })
        // The RN-queued event is reported to its notification.
        assertTrue(snapshot().any { it.path == "/v1/apps/app-fixture/notifications/notification-fixture/events" })
        // Session counters untouched by a start with no foreground.
        assertEquals(3, runtime.deviceStore.getSessionCount())
        assertEquals(45_000L, runtime.deviceStore.getSessionTimeMs())
        assertEquals(mapOf("plan" to "fixture-plan", "tier" to "fixture-tier"), runtime.deviceStore.getTags())
    }

    @Test
    fun tokenChangedFixtureReRegistersWithTheNewTokenAndKeepsTheId() {
        writeRegisteredFixture()
        val runtime = runtime(NEW_TOKEN)

        start(runtime)
        awaitUntil { runtime.deviceStore.getLastToken() == NEW_TOKEN }

        val register = registrations().single()
        assertEquals(NEW_TOKEN, JSONObject(register.body.readUtf8()).getString("token"))
        assertEquals(DEVICE_ID, runtime.deviceStore.getDeviceId())
        assertTrue(deviceIdChanges.isEmpty())
    }

    @Test
    fun fixtureAnsweredWithANewServerRowAdoptsItAndResetsAcks() {
        // RN: a different id after a stored one is a new server row; acks are cleared and re-sent.
        writeRegisteredFixture()
        serverDeviceId = "device-fixture-new-row"
        val runtime = runtime(TOKEN)

        start(runtime)
        awaitUntil { patches().any { it.has("sdk_version") } }

        assertEquals("device-fixture-new-row", runtime.deviceStore.getDeviceId())
        assertEquals(listOf("device-fixture-new-row"), deviceIdChanges.toList())
        assertEquals(NottiSdkVersion.CURRENT, patches().first { it.has("sdk_version") }.getString("sdk_version"))
    }

    private companion object {
        const val DEVICE_ID = "device-fixture-rn"
        const val TOKEN = "token-fixture-rn"
        const val NEW_TOKEN = "token-fixture-new"
    }
}
