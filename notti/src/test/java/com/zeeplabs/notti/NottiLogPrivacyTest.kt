package com.zeeplabs.notti

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Looper
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.RemoteMessage
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * No personal data reaches the logs (CORE-15, CORE-17, AD-011). The runtime runs at
 * [NottiLogger.Level.VERBOSE] with a capturing sink through a full flow whose every request
 * after registration fails (terminal 4xx, so no backoff), with sentinel values for every
 * personal or identifying input. Any captured line containing a sentinel fails the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiLogPrivacyTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val server = MockWebServer()
    private val lines: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val executors = mutableListOf<ExecutorService>()

    @Before
    fun setUp() {
        NottiRuntime.resetForTest()
        NottiLogger.level = NottiLogger.Level.VERBOSE
        NottiLogger.sink = { level, message -> lines += "$level $message" }
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.method == "POST" && request.path!!.endsWith("/devices")) {
                        MockResponse().setResponseCode(200).setBody("""{"id":"$DEVICE_ID","tags":{"$TAG_KEY":"$TAG_VALUE"}}""")
                    } else {
                        // Terminal failure whose body carries a sentinel: the body must not be logged.
                        MockResponse().setResponseCode(400).setBody("""{"error":"$BODY_SENTINEL $EMAIL"}""")
                    }
            }
        server.start()
    }

    @After
    fun tearDown() {
        NottiLogger.level = NottiLogger.Level.ERROR
        NottiLogger.sink = NottiLogger.defaultSink
        Notti.resetForTest()
        NottiNotificationClickRelay.reset()
        NottiActivityLifecycleListener.resetRegistrationForTest()
        NottiRuntime.resetForTest()
        executors.forEach { it.shutdownNow() }
        server.shutdown()
    }

    private fun runtime(token: () -> Task<String>): NottiRuntime {
        val prefs = app.getSharedPreferences("notti_log_privacy_test", Context.MODE_PRIVATE)
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
                    firebaseToken = token,
                )
            },
            executorFactory = { Executors.newSingleThreadExecutor().also { executors += it } },
        )
    }

    private val baseUrl: String
        get() = server.url("/$BASE_PATH").toString().trimEnd('/')

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met in time" }
            Thread.sleep(20)
        }
    }

    private fun awaitExecutorsIdle() {
        executors.filterNot { it.isShutdown }.forEach { it.submit {}.get(10, TimeUnit.SECONDS) }
    }

    private fun assertNoSentinel() {
        val captured = synchronized(lines) { lines.toList() }
        assertTrue("the flow must log something, or the test proves nothing", captured.isNotEmpty())
        for (line in captured) {
            for (sentinel in SENTINELS) {
                if (line.contains(sentinel)) fail("log line leaks \"$sentinel\": $line")
            }
        }
    }

    @Test
    fun fullFlowWithFailuresLogsNoSentinel() {
        val runtime = runtime { Tasks.forResult(TOKEN) }
        val core = runtime.start(APP_ID, CLIENT_KEY, baseUrl)
        awaitUntil { runtime.deviceStore.getDeviceId() == DEVICE_ID }

        core.login(EXTERNAL_USER_ID)
        core.setEmail(EMAIL)
        core.setPhone(PHONE)
        core.mutateTags(mapOf(TAG_KEY to TAG_VALUE, "fixture" to TAG_VALUE), null)
        runtime.enqueueEvent(NOTIFICATION_ID, DELIVERY_ID, NottiEventType.OPENED)
        awaitUntil { runtime.eventStore.all().isEmpty() }
        awaitExecutorsIdle()
        core.onTokenRefreshed(TOKEN)
        awaitExecutorsIdle()

        assertTrue(lines.any { it.contains("failed") })
        assertNoSentinel()
    }

    @Test
    fun tokenFetchFailureLogsTheClassNotTheMessage() {
        val runtime = runtime { Tasks.forException(IOException("$EXCEPTION_SENTINEL $baseUrl")) }
        runtime.start(APP_ID, CLIENT_KEY, baseUrl)
        awaitExecutorsIdle()

        assertTrue(lines.any { it.contains("IOException") })
        assertNoSentinel()
    }

    /** API-15: the same flow through the public facade, with the level set through `Notti.logLevel`. */
    @Test
    fun facadeFlowAtVerboseLogsNoSentinel() {
        NottiLogger.level = NottiLogger.Level.ERROR
        Notti.logLevel = NottiLogLevel.VERBOSE
        val runtime = runtime { Tasks.forResult(TOKEN) }
        NottiRuntime.setForTest(runtime)

        Notti.Notifications.addReceivedListener { throw IllegalStateException(EXCEPTION_SENTINEL) }
        Notti.Notifications.addClickedListener { throw IllegalStateException(EXCEPTION_SENTINEL) }
        Notti.addDeviceIdChangedListener { throw IllegalStateException(EXCEPTION_SENTINEL) }
        Notti.User.setEmail(EMAIL)
        Notti.login(EXTERNAL_USER_ID)
        Notti.initialize(app, APP_ID, CLIENT_KEY, NottiOptions(baseUrl))
        awaitUntil { runtime.deviceStore.getDeviceId() == DEVICE_ID }

        Notti.login(EXTERNAL_USER_ID)
        Notti.User.setPhone(PHONE)
        Notti.User.addTags(mapOf(TAG_KEY to TAG_VALUE))
        Notti.User.removeTag(TAG_KEY)
        Notti.setSubscription(false)
        Notti.setLocationSharingEnabled(true)
        Notti.setLocationSharingEnabled(false)
        Notti.Notifications.requestPermission { }
        deliverRemoteMessage(
            runtime,
            RemoteMessage(
                Bundle().apply {
                    putString("notification_id", NOTIFICATION_ID)
                    putString("delivery_id", DELIVERY_ID)
                    putString(TAG_KEY, EMAIL)
                },
            ),
        )
        NottiNotificationClickRelay.emit(ParsedNotification(PHONE, EMAIL, mapOf(TAG_KEY to TAG_VALUE)))
        awaitUntil { runtime.eventStore.all().isEmpty() }
        awaitExecutorsIdle()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(lines.any { it.contains("listener failed - IllegalStateException") })
        assertNoSentinel()
    }

    private companion object {
        const val APP_ID = "sntl-app-7f3"
        const val CLIENT_KEY = "sntl-key-7f3"
        const val TOKEN = "sntl-token-7f3"
        const val DEVICE_ID = "sntl-device-7f3"
        const val EXTERNAL_USER_ID = "sntl-user-7f3"
        const val EMAIL = "sntl-mail-7f3@example.invalid"
        const val PHONE = "+5511900007731"
        const val TAG_KEY = "sntl-tagkey-7f3"
        const val TAG_VALUE = "sntl-tagval-7f3"
        const val NOTIFICATION_ID = "sntl-notif-7f3"
        const val DELIVERY_ID = "sntl-delivery-7f3"
        const val BASE_PATH = "sntl-base-7f3"
        const val BODY_SENTINEL = "sntl-body-7f3"
        const val EXCEPTION_SENTINEL = "sntl-exc-7f3"
        val SENTINELS =
            listOf(
                APP_ID,
                CLIENT_KEY,
                TOKEN,
                DEVICE_ID,
                EXTERNAL_USER_ID,
                EMAIL,
                PHONE,
                TAG_KEY,
                TAG_VALUE,
                NOTIFICATION_ID,
                DELIVERY_ID,
                BASE_PATH,
                BODY_SENTINEL,
                EXCEPTION_SENTINEL,
            )
    }
}
