package com.zeeplabs.notti

import android.app.Application
import android.content.Context
import android.os.Bundle
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.RemoteMessage
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
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
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** The FCM service on [NottiRuntime] (PUSH-02, PUSH-03): received relay, lazy runtime, token to the live core. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiFirebaseMessagingServiceRuntimeTest {
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
                    return MockResponse().setResponseCode(200).setBody(JSONObject().put("id", "device-fixture").toString())
                }
            }
        server.start()
    }

    @After
    fun tearDown() {
        NottiRuntime.resetForTest()
        executors.forEach { it.shutdownNow() }
        server.shutdown()
    }

    private fun service(): NottiFirebaseMessagingService = Robolectric.setupService(NottiFirebaseMessagingService::class.java)

    private fun message(vararg pairs: Pair<String, String>): RemoteMessage =
        RemoteMessage(Bundle().apply { pairs.forEach { (key, value) -> putString(key, value) } })

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met in time" }
            Thread.sleep(20)
        }
    }

    @Test
    fun messageWithNoRuntimeCreatesItAndPersistsTheReceivedEvent() {
        assertNull(NottiRuntime.peek())

        service().onMessageReceived(message("notification_id" to "notification-fixture", "delivery_id" to "delivery-fixture"))

        val runtime = NottiRuntime.peek()
        assertNotNull(runtime)
        assertNull(runtime!!.core)
        val stored = runtime.eventStore.all().single()
        assertEquals("notification-fixture", stored.notificationId)
        assertEquals("delivery-fixture", stored.deliveryId)
        assertEquals(NottiEventType.RECEIVED, stored.type)
    }

    @Test
    fun messageIsHandedToTheReceivedCallbackParsed() {
        val received = mutableListOf<ParsedNotification>()
        NottiRuntime.get(app).onNotificationReceived = { received += it }

        service().onMessageReceived(message("gcm.n.e" to "1", "gcm.n.title" to "Title fixture", "plan" to "fixture"))

        assertEquals(listOf(ParsedNotification("Title fixture", null, mapOf("plan" to "fixture"))), received)
    }

    @Test
    fun resetForTestClearsTheReceivedCallback() {
        val runtime = NottiRuntime.get(app)
        runtime.onNotificationReceived = { }

        NottiRuntime.resetForTest()

        assertNull(runtime.onNotificationReceived)
    }

    @Test
    fun tokenWithNoRuntimeIsDroppedWithoutCreatingOne() {
        service().onNewToken("token-fixture")

        assertNull(NottiRuntime.peek())
    }

    @Test
    fun tokenReachesTheLiveCore() {
        val prefs = app.getSharedPreferences("notti_service_runtime_test", Context.MODE_PRIVATE)
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
            )
        NottiRuntime.setForTest(runtime)
        val core = runtime.start("app-fixture", "key-fixture", server.url("/").toString().trimEnd('/'))
        assertSame(core, NottiRuntime.peek()?.core)

        service().onNewToken("refreshed-token-fixture")

        fun tokensSent() =
            synchronized(requests) {
                requests
                    .filter { it.method == "POST" && it.path == "/v1/apps/app-fixture/devices" }
                    .map { JSONObject(it.body.clone().readUtf8()).optString("token") }
            }
        awaitUntil { "refreshed-token-fixture" in tokensSent() }
        assertTrue(tokensSent().toString(), "token-fixture" in tokensSent())
    }
}
