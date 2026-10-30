package com.zeeplabs.notti

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import okhttp3.OkHttpClient
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The process container of the core (CORE-02..05): the one owner of what RN kept on the
 * `NottiModule` companion. Created lazily by [get] on the first touch from any side (process hook,
 * FCM service, click detection, `initialize`), so the event store, the session gate and the device
 * store exist before any core (cold-start invariant, CORE-04). [ensureCore] builds the one core wired
 * to the Android providers (RN's lazy core), [start] initializes it; [enqueueEvent] is the
 * detection-site entry.
 */
internal class NottiRuntime internal constructor(
    context: Context,
    prefs: SharedPreferences,
    providersFactory: ((NottiDeviceStore, () -> Activity?) -> NottiAndroidProviders)? = null,
    private val executorFactory: () -> ExecutorService = { newDaemonExecutor("notti-io") },
    private val clock: () -> Long = { System.currentTimeMillis() },
    permissionRequester: ((callback: (Boolean) -> Unit) -> Unit)? = null,
) {
    private val appContext: Context = context.applicationContext ?: context

    /** One foreground session per process foreground, across core re-creation. */
    val sessionGate = NottiCore.SessionGate()

    /** The process-wide push event queue; detection sites enqueue here before any core exists. */
    val eventStore = NottiEventStore(prefs)

    val deviceStore = NottiDeviceStore(prefs)

    /** Weak: a destroyed or collected Activity is never held (no leak). */
    @Volatile
    private var currentActivityRef: WeakReference<Activity>? = null

    /**
     * The last resumed Activity not yet destroyed (RN `ReactContext.currentActivity`), set by
     * [NottiActivityLifecycleListener]; read by the permission rationale and the permission request.
     */
    val currentActivity: () -> Activity? = { currentActivityRef?.get() }

    /** Called by the lifecycle listener on every resume: the resumed Activity becomes current. */
    @Synchronized
    internal fun activityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    /** Called by the lifecycle listener on destroy: clears the current Activity only if it is [activity]. */
    @Synchronized
    internal fun activityDestroyed(activity: Activity) {
        if (currentActivityRef?.get() === activity) currentActivityRef = null
    }

    /**
     * Receives each foreground FCM message the SDK handles (RN `emitNotificationReceived`); null until
     * `public-api` sets it. No buffer: RN emitted only to a live module.
     */
    @Volatile
    var onNotificationReceived: ((ParsedNotification) -> Unit)? = null

    /** Receives each device id the backend assigns (RN `emitDeviceIdChanged`); null until `public-api` sets it. */
    @Volatile
    var onDeviceIdChanged: ((String) -> Unit)? = null

    /** The In-App engine's lifecycle hooks; null until `in-app-messages` sets them. */
    @Volatile
    var inAppLifecycle: NottiInAppLifecycle? = null

    /** The live core, if [ensureCore] ran and no [stop] cleared it. */
    @Volatile
    var core: NottiCore? = null
        private set

    private var coreExecutor: ExecutorService? = null

    /** Reverse geocoding (blocking network call), one per runtime as one per module in RN. */
    private val geoExecutor: ExecutorService = newDaemonExecutor("notti-geo")

    /** The API 33+ dialog from the current Activity (RN `requestNativePermission`), the core's default requester. */
    private val defaultPermissionRequester = NottiPermissionRequester(appContext, deviceStore, currentActivity)

    private val providers: NottiAndroidProviders =
        providersFactory?.invoke(deviceStore) { currentActivity() }
            ?: NottiAndroidProviders(appContext, deviceStore, { currentActivity() }, geoExecutor)

    private val corePermissionRequester: (callback: (Boolean) -> Unit) -> Unit =
        permissionRequester ?: defaultPermissionRequester::request

    /**
     * The live core, or a new one with the Android providers, a new `notti-io` executor, the shared
     * gate and stores, not initialized (RN's lazy core: calls made before `initialize` are queued by
     * the core). One core per runtime until [stop].
     */
    @Synchronized
    fun ensureCore(): NottiCore {
        core?.let { return it }
        val io = executorFactory()
        val newCore =
            NottiCore(
                deviceStore = deviceStore,
                eventStore = eventStore,
                apiClientFactory = { id, key, url -> NottiApiClient(OkHttpClient(), url, id, key) },
                tokenProvider = { callback -> providers.fcmToken(io, callback) },
                permissionRequester = corePermissionRequester,
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
                onDeviceIdChanged = { id -> onDeviceIdChanged?.invoke(id) },
                clock = clock,
                sessionGate = sessionGate,
                inApp = NottiInAppHooks.None,
            )
        core = newCore
        coreExecutor = io
        // The process onStart for this launch usually fired before the core existed: replay it.
        NottiForegroundObserver.onCoreCreated(newCore)
        return newCore
    }

    /**
     * Initializes the core of [ensureCore] with [NottiSdkVersion.CURRENT] (RN `NottiModule.initialize`
     * on its lazy core). The core is re-initialized in place: an identical config is a no-op (SDK-07),
     * a different one registers again (AD-014).
     */
    @Synchronized
    fun start(
        appId: String,
        clientKey: String,
        baseUrl: String,
    ): NottiCore = ensureCore().also { it.initialize(appId, clientKey, baseUrl, NottiSdkVersion.CURRENT) }

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

        /** Test-only: makes [runtime] the process runtime (RN `setEventStoreForTest`), dropping any previous one. */
        internal fun setForTest(runtime: NottiRuntime) {
            synchronized(this) {
                instance?.shutdown()
                instance = runtime
            }
        }

        /** Test-only: drops the process runtime, its callbacks and its executors. */
        internal fun resetForTest() {
            synchronized(this) {
                instance?.onNotificationReceived = null
                instance?.onDeviceIdChanged = null
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
