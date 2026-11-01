package com.zeeplabs.notti

import android.app.Application
import android.content.Context
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** `Notti.User`, `setSubscription`, `setLocationSharingEnabled` through the facade (API-06..08). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiUserFacadeTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()
    private val requests: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())
    private val executors = Collections.synchronizedList(mutableListOf<ExecutorService>())
    private val logged = Collections.synchronizedList(mutableListOf<String>())
    private lateinit var runtime: NottiRuntime

    @Before
    fun setUp() {
        NottiRuntime.resetForTest()
        Notti.resetForTest()
        NottiLogger.sink = { _, message -> logged += message }
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
        val prefs = app.getSharedPreferences("notti_user_facade_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        runtime =
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
    }

    @After
    fun tearDown() {
        NottiLogger.sink = NottiLogger.defaultSink
        Notti.resetForTest()
        NottiRuntime.resetForTest()
        executors.forEach { it.shutdownNow() }
        server.shutdown()
    }

    private val mockUrl: String
        get() = server.url("/").toString().trimEnd('/')

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

    private fun patches(): List<JSONObject> =
        synchronized(requests) { requests.filter { it.method == "PATCH" } }.map { JSONObject(it.body.clone().readUtf8()) }

    private fun tagPatches(): List<Map<String, String>> =
        patches().filter { it.has("tags") }.map { json ->
            val tags = json.getJSONObject("tags")
            tags.keys().asSequence().associateWith { tags.getString(it) }
        }

    private fun initialize() {
        Notti.initialize(app, "app-fixture", "key-fixture", NottiOptions(mockUrl))
        awaitUntil { runtime.deviceStore.getDeviceId() == "device-fixture" }
        awaitIdle()
    }

    @Test
    fun addTagAndAddTagsPatchTheMergedTags() {
        initialize()

        Notti.User.addTag("plan", "fixture-plan")
        awaitUntil { tagPatches().isNotEmpty() }
        Notti.User.addTags(mapOf("tier" to "fixture-tier", "region" to "fixture-region"))
        awaitUntil { tagPatches().size == 2 }

        assertEquals(mapOf("plan" to "fixture-plan"), tagPatches()[0])
        assertEquals(
            mapOf("plan" to "fixture-plan", "tier" to "fixture-tier", "region" to "fixture-region"),
            tagPatches()[1],
        )
    }

    @Test
    fun nullEntriesFromJavaAreDroppedAndCountedNotLogged() {
        initialize()
        // What a Java caller can build: a Map<String, String> holding null keys and values (AD-018).
        val tags = HashMap<String?, String?>()
        tags["plan"] = "fixture-plan"
        tags["tier"] = null
        tags[null] = "fixture-orphan"
        @Suppress("UNCHECKED_CAST")
        Notti.User.addTags(tags as Map<String, String>)
        awaitUntil { tagPatches().isNotEmpty() }
        val keys = arrayListOf<String?>("plan", null)
        @Suppress("UNCHECKED_CAST")
        Notti.User.removeTags(keys as List<String>)
        awaitUntil { tagPatches().size == 2 }

        assertEquals(mapOf("plan" to "fixture-plan"), tagPatches()[0])
        assertEquals(emptyMap<String, String>(), tagPatches()[1])
        assertTrue(logged.contains("Notti.User.addTags: 2 null entries dropped"))
        assertTrue(logged.contains("Notti.User.removeTags: 1 null items dropped"))
        assertFalse(logged.any { it.contains("fixture-orphan") || it.contains("fixture-plan") })
    }

    @Test
    fun nullArgumentsAreLoggedByMethodNameAndSendNothing() {
        initialize()
        val before = patches().size

        Notti.login(null)
        Notti.User.setEmail(null)
        Notti.User.addTag("plan", null)
        awaitIdle()

        assertEquals(before, patches().size)
        assertTrue(logged.contains("Notti.login: null argument - ignored"))
        assertTrue(logged.contains("Notti.User.setEmail: null argument - ignored"))
        assertTrue(logged.contains("Notti.User.addTag: null argument - ignored"))
    }

    @Test
    fun removeTagAndRemoveTagsPatchTheRemainingTags() {
        initialize()
        Notti.User.addTags(mapOf("a" to "1", "b" to "2", "c" to "3"))
        awaitUntil { tagPatches().size == 1 }

        Notti.User.removeTag("a")
        awaitUntil { tagPatches().size == 2 }
        Notti.User.removeTags(listOf("b"))
        awaitUntil { tagPatches().size == 3 }

        assertEquals(mapOf("b" to "2", "c" to "3"), tagPatches()[1])
        assertEquals(mapOf("c" to "3"), tagPatches()[2])
    }

    @Test
    fun setEmailAndClearEmailPatchTheEmailField() {
        initialize()

        Notti.User.setEmail("user@example.invalid")
        awaitUntil { patches().any { it.optString("email") == "user@example.invalid" } }
        Notti.User.clearEmail()

        awaitUntil { patches().any { it.has("email") && it.isNull("email") } }
        assertFalse(patches().any { it.has("tags") })
    }

    @Test
    fun setPhoneAndClearPhonePatchThePhoneField() {
        initialize()

        Notti.User.setPhone("+10000000000")
        awaitUntil { patches().any { it.optString("phone") == "+10000000000" } }
        Notti.User.clearPhone()

        awaitUntil { patches().any { it.has("phone") && it.isNull("phone") } }
    }

    @Test
    fun setSubscriptionFalseSendsTheUnsubscribeTimestamp() {
        initialize()

        Notti.setSubscription(false)
        awaitUntil { patches().any { it.has("subscribed") } }
        Notti.setSubscription(true)
        awaitUntil { patches().count { it.has("subscribed") } == 2 }

        val (off, on) = patches().filter { it.has("subscribed") }
        assertFalse(off.getBoolean("subscribed"))
        assertTrue(off.has("last_unsubscribed_at"))
        assertTrue(on.getBoolean("subscribed"))
        assertFalse(on.has("last_unsubscribed_at"))
    }

    @Test
    fun setLocationSharingEnabledWritesOnTheCallersThread() {
        initialize()
        assertFalse(runtime.deviceStore.getLocationSharingEnabled())

        Notti.setLocationSharingEnabled(true)
        assertTrue(runtime.deviceStore.getLocationSharingEnabled())

        Notti.setLocationSharingEnabled(false)
        assertFalse(runtime.deviceStore.getLocationSharingEnabled())
        assertTrue(runtime.deviceStore.getPendingCountryClear())
        awaitUntil { patches().any { it.has("country") && it.isNull("country") } }
    }

    @Test
    fun tagsAndSubscriptionBeforeInitializeAreIgnoredByTheCore() {
        Notti.User.addTag("plan", "sentinel-plan")
        Notti.User.removeTags(listOf("plan"))
        Notti.setSubscription(false)

        assertTrue(logged.toString(), logged.contains("Notti.mutateTags: called before initialize - ignored"))
        assertTrue(logged.toString(), logged.contains("Notti.setSubscription: called before initialize - ignored"))
        initialize()
        assertFalse(patches().any { it.has("tags") || it.has("subscribed") })
        assertFalse(logged.any { it.contains("sentinel") })
    }

    @Test
    fun emailAndPhoneBeforeInitializeArePersistedAndSentAtRegistration() {
        Notti.User.setEmail("user@example.invalid")
        Notti.User.setPhone("+10000000000")
        assertEquals("user@example.invalid", runtime.deviceStore.getEmail())
        assertEquals("+10000000000", runtime.deviceStore.getPhone())

        initialize()

        awaitUntil { patches().any { it.optString("email") == "user@example.invalid" } }
        awaitUntil { patches().any { it.optString("phone") == "+10000000000" } }
    }

    @Test
    fun locationSharingTurnedOffBeforeInitializeClearsTheCountryAtRegistration() {
        Notti.setLocationSharingEnabled(true)
        Notti.setLocationSharingEnabled(false)
        assertTrue(runtime.deviceStore.getPendingCountryClear())

        initialize()

        awaitUntil { patches().any { it.has("country") && it.isNull("country") } }
    }

    @Test
    fun userCallsWithNoRuntimeAreDroppedWithAWarn() {
        NottiRuntime.resetForTest()
        NottiLogger.level = NottiLogger.Level.WARN
        try {
            Notti.User.addTag("plan", "sentinel-plan")
            Notti.User.setEmail("sentinel@example.invalid")
            Notti.setLocationSharingEnabled(true)
        } finally {
            NottiLogger.level = NottiLogger.Level.ERROR
        }

        assertEquals(
            listOf(
                "Notti.User.addTag: called before initialize with no runtime - dropped",
                "Notti.User.setEmail: called before initialize with no runtime - dropped",
                "Notti.setLocationSharingEnabled: called before initialize with no runtime - dropped",
            ),
            logged.toList(),
        )
    }
}
