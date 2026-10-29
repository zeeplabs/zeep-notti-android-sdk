package com.zeeplabs.notti

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import okhttp3.OkHttpClient
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The process container of the core (CORE-02..05): the one owner of what RN kept on the
 * `NottiModule` companion. Created lazily by [get] on the first touch from any side (process hook,
 * FCM service, click detection, `initialize`), so the event store, the session gate and the device
 * store exist before any core (cold-start invariant, CORE-04). [start] builds a core wired to the
 * Android providers; [enqueueEvent] is the detection-site entry.
 */
internal class NottiRuntime internal constructor(
    context: Context,
    prefs: SharedPreferences,
    providersFactory: ((NottiDeviceStore, () -> Activity?) -> NottiAndroidProviders)? = null,
    private val executorFactory: () -> ExecutorService = { newDaemonExecutor("notti-io") },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val appContext: Context = context.applicationContext ?: context

    /** One foreground session per process foreground, across core re-creation. */
    val sessionGate = NottiCore.SessionGate()

    /** The process-wide push event queue; detection sites enqueue here before any core exists. */
    val eventStore = NottiEventStore(prefs)

    val deviceStore = NottiDeviceStore(prefs)

    /** The resumed Activity, for the permission rationale read. `push-integration` sets it. */
    @Volatile
    var currentActivity: () -> Activity? = { null }

    /** The In-App engine's lifecycle hooks; null until `in-app-messages` sets them. */
    @Volatile
    var inAppLifecycle: NottiInAppLifecycle? = null

    /** The live core, if [start] ran and no [stop] cleared it. */
    @Volatile
    var core: NottiCore? = null
        private set

    private var coreExecutor: ExecutorService? = null

    /** Reverse geocoding (blocking network call), one per runtime as one per module in RN. */
    private val geoExecutor: ExecutorService = newDaemonExecutor("notti-geo")

    private val providers: NottiAndroidProviders =
        providersFactory?.invoke(deviceStore) { currentActivity() }
            ?: NottiAndroidProviders(appContext, deviceStore, { currentActivity() }, geoExecutor)

    /**
     * Builds a core with the Android providers, a new `notti-io` executor, the shared gate and
     * stores, then initializes it with [NottiSdkVersion.CURRENT]. A live core is replaced and its
     * executor shut down (RN reload semantics). Config comparison and no-op rules belong to
     * `public-api`.
     */
    @Synchronized
    fun start(
        appId: String,
        clientKey: String,
        baseUrl: String,
        permissionRequester: (callback: (Boolean) -> Unit) -> Unit = { it(false) },
        onDeviceIdChanged: (String) -> Unit = {},
    ): NottiCore {
        val io = executorFactory()
        val newCore =
            NottiCore(
                deviceStore = deviceStore,
                eventStore = eventStore,
                apiClientFactory = { id, key, url -> NottiApiClient(OkHttpClient(), url, id, key) },
                tokenProvider = { callback -> providers.fcmToken(io, callback) },
                permissionRequester = permissionRequester,
                logger = { message -> NottiLogger.error(message) },
                executor = io,
                versionProvider = { providers.appVersion() },
                deviceOsProvider = { providers.deviceOs() },
                deviceModelProvider = { providers.deviceModel() },
                timezoneProvider = { providers.timezoneId() },
                languageProvider = { providers.language() },
                permissionStatusProvider = { callback -> providers.permissionStatus(callback) },
                hasLocationPermission = { providers.hasLocationPermission() },
                countryProvider = { callback -> providers.country(callback) },
                onDeviceIdChanged = onDeviceIdChanged,
                clock = clock,
                sessionGate = sessionGate,
                inApp = NottiInAppHooks.None,
            )
        val oldExecutor = coreExecutor
        core = newCore
        coreExecutor = io
        oldExecutor?.shutdown()
        // The process onStart for this launch usually fired before the core existed: replay it.
        NottiForegroundObserver.onCoreCreated(newCore)
        newCore.initialize(appId, clientKey, baseUrl, NottiSdkVersion.CURRENT)
        return newCore
    }

    /** Clears [core] and shuts its executor down, only if [core] is still the live one. */
    @Synchronized
    fun stop(core: NottiCore) {
        if (this.core !== core) return
        this.core = null
        coreExecutor?.shutdown()
        coreExecutor = null
    }

    /** Persists a push event, then asks the live core (if any) to flush (RN `enqueueEvent`). */
    fun enqueueEvent(
        notificationId: String,
        deliveryId: String,
        type: String,
    ) {
        eventStore.enqueue(notificationId, deliveryId, type)
        core?.onNetworkAvailable()
    }

    private fun shutdown() {
        core?.let { stop(it) }
        geoExecutor.shutdown()
    }

    companion object {
        /** The RN 0.8.0 file name; changing it loses every install's state (CORE-14). */
        const val PREFS_NAME = "notti_prefs"

        @Volatile
        private var instance: NottiRuntime? = null

        /** The process runtime, created on first use; keeps only the application context. */
        fun get(context: Context): NottiRuntime {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val app = context.applicationContext ?: context
                return NottiRuntime(app, app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
                    .also { instance = it }
            }
        }

        /** The runtime if one exists; for callers that must not create it (observers). */
        fun peek(): NottiRuntime? = instance

        /** Test-only: drops the process runtime and stops its executors. */
        internal fun resetForTest() {
            synchronized(this) {
                instance?.shutdown()
                instance = null
            }
        }

        private fun newDaemonExecutor(name: String): ExecutorService =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, name).apply { isDaemon = true }
            }
    }
}
