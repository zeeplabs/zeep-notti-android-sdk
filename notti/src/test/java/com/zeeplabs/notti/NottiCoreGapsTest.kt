package com.zeeplabs.notti

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

/**
 * Direct core-layer tests for the RN `sdk-core-v1` requirements whose RN validation had only
 * indirect or declared-scope evidence (CORE-11). Each test names its ID. Parts owned elsewhere:
 *
 * - SDK-04: the build-time `google-services.json` resolution and the host-facing error wording
 *   belong to `push-integration` / `public-api`; here, the runtime path with no FirebaseApp.
 * - SDK-08: the real OS dialog (`POST_NOTIFICATIONS` via an Activity) belongs to
 *   `push-integration`; here, the injected requester is the one the core calls, and its answer
 *   reaches the backend.
 * - SDK-12/13: the `Notti.User.addTag(s)`/`removeTag(s)` facade belongs to `public-api`; here, the
 *   core merge and the full-map PATCH.
 * - SDK-17: emitting `notificationReceived` to the host (listener API) belongs to `public-api`
 *   and the FCM receive wiring and `parseRemoteMessage` to `push-integration`; the core has no
 *   part in it, so no test here (listed in traceability).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiCoreGapsTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()
    private val requests: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())
    private val lines: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val executors = mutableListOf<ExecutorService>()

    @Before
    fun setUp() {
        NottiRuntime.resetForTest()
        NottiLogger.sink = { _, message -> lines += message }
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val body = JSONObject().put("id", "device-fixture").put("tags", JSONObject().put("seed", "fixture-seed"))
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
        NottiLogger.sink = NottiLogger.defaultSink
        NottiRuntime.resetForTest()
        executors.forEach { it.shutdownNow() }
        server.shutdown()
    }

    private val baseUrl: String
        get() = server.url("/").toString().trimEnd('/')

    private fun runtime(permissionRequester: ((callback: (Boolean) -> Unit) -> Unit)? = null): NottiRuntime {
        val prefs = app.getSharedPreferences("notti_gaps_test", Context.MODE_PRIVATE)
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
                    firebaseToken = { Tasks.forResult("token-fixture") },
                )
            },
            executorFactory = { Executors.newSingleThreadExecutor().also { executors += it } },
            permissionRequester = permissionRequester,
        )
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

    private fun tagPatches(): List<Map<String, String>> =
        synchronized(requests) { requests.toList() }
            .filter { it.method == "PATCH" }
            .map { JSONObject(it.body.clone().readUtf8()) }
            .filter { it.has("tags") }
            .map { json ->
                val tags = json.getJSONObject("tags")
                tags.keys().asSequence().associateWith { tags.getString(it) }
            }

    @Test
    fun sdk04MissingFirebaseAppNeitherCrashesNorRegistersAndLogsWhy() {
        // Production providers: FirebaseMessaging.getInstance() throws with no FirebaseApp.
        val runtime = NottiRuntime.get(app)

        runtime.start("app-fixture", "key-fixture", baseUrl)
        awaitUntil { lines.any { it.contains("no push token available") } }

        assertTrue(lines.any { it == "Notti: failed to fetch FCM token - IllegalStateException" })
        assertEquals(0, server.requestCount)
        assertEquals(null, runtime.deviceStore.getDeviceId())
    }

    @Test
    fun sdk08RequestPermissionCallsTheInjectedRequesterAndPatchesItsAnswer() {
        var prompts = 0
        val runtime =
            runtime(permissionRequester = { callback ->
                prompts++
                callback(true)
            })
        val core = runtime.start("app-fixture", "key-fixture", baseUrl)
        awaitUntil { runtime.deviceStore.getDeviceId() == "device-fixture" }

        var answered: Boolean? = null
        core.requestPermission { answered = it }
        awaitUntil { runtime.deviceStore.getSubscribedOrNull() == true }

        assertEquals(1, prompts)
        assertEquals(true, answered)
        val subscribed =
            synchronized(requests) { requests.toList() }
                .filter { it.method == "PATCH" }
                .map { JSONObject(it.body.clone().readUtf8()) }
                .filter { it.has("subscribed") }
        assertEquals(true, subscribed.single().getBoolean("subscribed"))
    }

    @Test
    fun sdk08DefaultRuntimeRequesterDeniesWithoutPromptingWhenNoActivityIsResumed() {
        val runtime = runtime()
        val core = runtime.start("app-fixture", "key-fixture", baseUrl)
        awaitUntil { runtime.deviceStore.getDeviceId() == "device-fixture" }

        var answered: Boolean? = null
        core.requestPermission { answered = it }
        awaitUntil { answered != null }
        awaitIdle()

        assertFalse(answered!!)
    }

    @Test
    fun sdk12AddTagsMergesIntoTheCachedMapAndPatchesTheFullMap() {
        val runtime = runtime()
        val core = runtime.start("app-fixture", "key-fixture", baseUrl)
        awaitUntil { runtime.deviceStore.getTags() == mapOf("seed" to "fixture-seed") }

        core.mutateTags(mapOf("plan" to "fixture-plan"), null)
        awaitUntil { tagPatches().isNotEmpty() }

        assertEquals(mapOf("seed" to "fixture-seed", "plan" to "fixture-plan"), tagPatches().single())
    }

    @Test
    fun sdk13RemoveTagsDropsTheKeyAndPatchesTheFullRemainingMap() {
        val runtime = runtime()
        val core = runtime.start("app-fixture", "key-fixture", baseUrl)
        awaitUntil { runtime.deviceStore.getTags() == mapOf("seed" to "fixture-seed") }

        core.mutateTags(mapOf("plan" to "fixture-plan", "tier" to "fixture-tier"), null)
        awaitUntil { tagPatches().size == 1 }
        core.mutateTags(null, listOf("plan"))
        awaitUntil { tagPatches().size == 2 }

        assertEquals(mapOf("seed" to "fixture-seed", "tier" to "fixture-tier"), tagPatches()[1])
    }
}
