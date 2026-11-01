package com.zeeplabs.notti

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

/**
 * The Notti SDK: push registration, user identity and profile, notification events, In-App messages.
 *
 * Call [initialize] once, as early as possible (ideally in `Application.onCreate`). Every other call
 * is safe before it: the SDK keeps what it can and sends it after the first registration. Nothing
 * here throws; invalid input is logged and ignored. Text, map and list parameters also accept `null`
 * from Java: the call is logged and ignored, and `null` keys, values or items are dropped (AD-018).
 * Listener and callback arguments must not be null. Listeners and callbacks run on the main thread.
 */
object Notti {
    /** RN `SAAS_BASE_URL`: the host used when [NottiOptions.baseUrl] is null. */
    internal const val DEFAULT_BASE_URL = "https://app.zeepnotti.app"

    private val deviceIdListeners = NottiListeners<String>("device id")

    /** Installed into the runtime's device-id slot whenever the facade reaches a runtime (design D3). */
    private val deviceIdSlot: (String) -> Unit = { deviceIdListeners.dispatch(it) }

    /**
     * Starts the SDK and registers the device. Only the application context of [context] is kept.
     * Calling again with the same [appId], [clientKey] and base URL does nothing; with a different one
     * the device registers again with the new values. Blank or null ids or an invalid base URL are logged
     * and ignored, and a previous valid configuration stays in effect. A null [options] means the defaults. Each valid call applies
     * [NottiOptions.opensInAppUrls], also when nothing else changed.
     */
    @JvmStatic
    @JvmOverloads
    fun initialize(
        context: Context,
        appId: String?,
        clientKey: String?,
        options: NottiOptions? = NottiOptions(),
    ) {
        if (appId == null || clientKey == null) return nullArgument("initialize")
        val validOptions = options ?: NottiOptions()
        try {
            val app = context.applicationContext ?: context
            if (app is Application) {
                NottiProcessHooks.installOnce(app)
            } else {
                NottiLogger.error("Notti.initialize: no Application context - process hooks not installed")
            }
            val runtime = NottiRuntime.get(app)
            wire(runtime)
            val baseUrl = validOptions.baseUrl ?: DEFAULT_BASE_URL
            // RN order: In-App gets the config before the core exists, so the session start that the
            // core's creation replays finds it (same main-thread FIFO as trackEvent).
            initializeInApp(appId, clientKey, baseUrl, validOptions)
            wirePushPermissionPort(app, runtime)
            runtime.start(appId, clientKey, baseUrl)
        } catch (t: Throwable) {
            NottiLogger.error("Notti.initialize: failed - ${t.javaClass.simpleName}")
        }
    }

    /** Links this device to your user id; sent after the first registration if called before it. */
    @JvmStatic
    fun login(externalUserId: String?) {
        if (externalUserId == null) return nullArgument("login")
        core("login")?.login(externalUserId)
    }

    /** Unlinks the device from the user and clears the user's email and phone on the backend. */
    @JvmStatic
    fun logout() {
        core("logout")?.logout()
    }

    /**
     * Turns push delivery to this device on or off on the backend. Turning it off records when the
     * user opted out. Ignored before [initialize] (logged).
     */
    @JvmStatic
    fun setSubscription(enabled: Boolean) {
        core("setSubscription")?.setSubscription(enabled)
    }

    /**
     * Opt-in for country-based targeting; default off. Saved at once on the calling thread. The SDK
     * never asks for the location permission: with it already granted by the app, the country is
     * read at the next session start. Turning it off also clears the country on the backend.
     */
    @JvmStatic
    fun setLocationSharingEnabled(enabled: Boolean) {
        core("setLocationSharingEnabled")?.setLocationSharingEnabled(enabled)
    }

    /**
     * Reports an app event by [name], for In-App messages triggered by that event. Kept from right after
     * [initialize] until the messages are known; ignored before [initialize] and when blank. Any thread.
     * The name is never logged or stored.
     */
    @JvmStatic
    fun trackEvent(name: String?) {
        if (name == null) return nullArgument("trackEvent")
        try {
            NottiInApp.get()?.trackEvent(name)
        } catch (t: Throwable) {
            NottiLogger.error("Notti.trackEvent: failed - ${t.javaClass.simpleName}")
        }
    }

    /** The Notti device id, or null until the first registration (or with no runtime yet). */
    @JvmStatic
    val deviceId: String?
        get() = NottiRuntime.peek()?.deviceStore?.getDeviceId()

    /** Calls [listener] on the main thread each time the backend assigns a new device id. */
    @JvmStatic
    fun addDeviceIdChangedListener(listener: NottiDeviceIdListener): NottiSubscription {
        NottiRuntime.peek()?.let(::wire)
        return deviceIdListeners.add { listener.onDeviceIdChanged(it) }
    }

    /** How much the SDK logs. Default [NottiLogLevel.ERROR]. */
    @JvmStatic
    var logLevel: NottiLogLevel
        get() = NottiLogger.level.toPublic()
        set(value) {
            NottiLogger.level = value.toLoggerLevel()
        }

    /**
     * The user's tags, email and phone. Tags are ignored before [initialize] (logged); email and
     * phone are saved and sent at the first registration.
     */
    object User {
        /** Sets one tag. */
        @JvmStatic
        fun addTag(
            key: String?,
            value: String?,
        ) {
            if (key == null || value == null) return nullArgument("User.addTag")
            core("User.addTag")?.mutateTags(add = mapOf(key to value), remove = null)
        }

        /** Sets several tags in one request. */
        @JvmStatic
        fun addTags(tags: Map<String, String>?) {
            if (tags == null) return nullArgument("User.addTags")
            core("User.addTags")?.mutateTags(add = nonNullEntries("User.addTags", tags), remove = null)
        }

        /** Removes one tag. */
        @JvmStatic
        fun removeTag(key: String?) {
            if (key == null) return nullArgument("User.removeTag")
            core("User.removeTag")?.mutateTags(add = null, remove = listOf(key))
        }

        /** Removes several tags in one request. */
        @JvmStatic
        fun removeTags(keys: List<String>?) {
            if (keys == null) return nullArgument("User.removeTags")
            core("User.removeTags")?.mutateTags(add = null, remove = nonNullItems("User.removeTags", keys))
        }

        @JvmStatic
        fun setEmail(email: String?) {
            if (email == null) return nullArgument("User.setEmail")
            core("User.setEmail")?.setEmail(email)
        }

        @JvmStatic
        fun clearEmail() {
            core("User.clearEmail")?.clearEmail()
        }

        @JvmStatic
        fun setPhone(phone: String?) {
            if (phone == null) return nullArgument("User.setPhone")
            core("User.setPhone")?.setPhone(phone)
        }

        @JvmStatic
        fun clearPhone() {
            core("User.clearPhone")?.clearPhone()
        }
    }

    /** Push permission and notification events. */
    object Notifications {
        private val receivedListeners = NottiListeners<NottiNotification>("received")

        /** Installed into the runtime's received slot whenever the facade reaches a runtime (design D3). */
        internal val receivedSlot: (ParsedNotification) -> Unit = { receivedListeners.dispatch(it.toPublic()) }

        /** The click relay's emitter while at least one clicked listener exists (API-11). */
        private val clickEmitter: (ParsedNotification) -> Unit = { clickedListeners.dispatch(it.toPublic()) }

        private val clickedListeners: NottiListeners<NottiNotification> =
            NottiListeners(
                "clicked",
                onFirstAdded = { NottiNotificationClickRelay.attach(clickEmitter) },
                onLastRemoved = { NottiNotificationClickRelay.detach(clickEmitter) },
            )

        /**
         * Shows the system notification-permission dialog (Android 13+) from the current Activity and
         * reports the answer once, on the main thread. Below Android 13 the permission is granted at
         * install and the answer is `true`. Before [initialize] the answer is `false` and no dialog
         * is shown.
         */
        @JvmStatic
        fun requestPermission(callback: NottiPermissionCallback) {
            val core = core("Notifications.requestPermission")
            if (core == null) {
                NottiMainThread.post { callback.onResult(false) }
                return
            }
            core.requestPermission { granted -> NottiMainThread.post { callback.onResult(granted) } }
        }

        /** Calls [listener] for each push received while the app is in the foreground. Not replayed. */
        @JvmStatic
        fun addReceivedListener(listener: NottiNotificationListener): NottiSubscription {
            NottiRuntime.peek()?.let(::wire)
            return receivedListeners.add { listener.onNotification(it) }
        }

        /**
         * Calls [listener] for each notification tap. While no clicked listener exists, the last tap
         * is kept for [takeInitialClick] instead; add the listener in `Application.onCreate` to get the
         * tap that launched the app as an event.
         */
        @JvmStatic
        fun addClickedListener(listener: NottiNotificationListener): NottiSubscription =
            clickedListeners.add { listener.onNotification(it) }

        /** The tap kept while no clicked listener existed (usually the one that launched the app), once. */
        @JvmStatic
        fun takeInitialClick(): NottiNotification? = NottiNotificationClickRelay.takePending()?.toPublic()

        internal fun resetForTest() {
            receivedListeners.clearForTest()
            clickedListeners.clearForTest()
            NottiNotificationClickRelay.detach(clickEmitter)
        }
    }

    /** In-App messages, shown by the SDK over the current Activity. */
    object InAppMessages {
        /**
         * Calls [listener] on the main thread for each tap on an In-App message button or image, before
         * the message closes. Listeners added before [initialize] are kept.
         */
        @JvmStatic
        fun addClickListener(listener: NottiInAppClickListener): NottiSubscription = NottiInAppClicks.listeners.add { listener.onClick(it) }
    }

    /** RN `NottiModule.initialize`: a valid config reaches In-App, with the URL option (design D5). */
    private fun initializeInApp(
        appId: String,
        clientKey: String,
        baseUrl: String,
        options: NottiOptions,
    ) {
        val coordinator = NottiInApp.get() ?: return
        val normalizedBaseUrl = NottiCore.normalizeBaseUrl(baseUrl)
        if (appId.isBlank() || clientKey.isBlank() || normalizedBaseUrl == null) return
        coordinator.onInitialized(appId, clientKey, normalizedBaseUrl)
        coordinator.urlOpener?.enabled = options.opensInAppUrls
    }

    /**
     * RN `wirePushPermissionPort` (design D4): the OS side of the `push_permission` button. The prompt goes
     * through [NottiCore.requestPermission], which sets the asked flag only when the prompt is shown.
     */
    private fun wirePushPermissionPort(
        app: Context,
        runtime: NottiRuntime,
    ) {
        val coordinator = NottiInApp.get() ?: return
        coordinator.pushPermissionPort =
            NottiPushPermissionAction(
                sdkInt = Build.VERSION.SDK_INT,
                notificationsEnabled = { NotificationManagerCompat.from(app).areNotificationsEnabled() },
                promptAlreadyShown = { runtime.deviceStore.getPermissionRequested() },
                hasActivity = { runtime.currentActivity() != null },
                showPrompt = { runtime.ensureCore().requestPermission { } },
                // A missing settings activity throws here and is caught by NottiPushPermissionAction.run.
                openNotificationSettings = {
                    runtime.currentActivity()?.let { activity ->
                        activity.startActivity(appNotificationSettingsIntent(Build.VERSION.SDK_INT, activity.packageName))
                    }
                },
                logger = { message -> NottiLogger.error(message) },
            )
    }

    /** A Java caller passed null where Kotlin declares a value (AD-018): logged by method name, ignored. */
    private fun nullArgument(method: String) {
        NottiLogger.error("Notti.$method: null argument - ignored")
    }

    /**
     * A copy of [map] without null keys or values, which a Java caller can put in a `Map<String, String>`
     * (AD-018). Typed `Map<*, *>` so the checks are real; drops are logged as a count only.
     */
    private fun nonNullEntries(
        method: String,
        map: Map<*, *>,
    ): Map<String, String> {
        val kept = LinkedHashMap<String, String>()
        for ((key, value) in map) {
            if (key is String && value is String) kept[key] = value
        }
        if (kept.size != map.size) NottiLogger.error("Notti.$method: ${map.size - kept.size} null entries dropped")
        return kept
    }

    /** A copy of [list] without null items (AD-018); drops are logged as a count only. */
    private fun nonNullItems(
        method: String,
        list: List<*>,
    ): List<String> {
        val kept = list.filterIsInstance<String>()
        if (kept.size != list.size) NottiLogger.error("Notti.$method: ${list.size - kept.size} null items dropped")
        return kept
    }

    /** The core of the process runtime (built lazily, RN parity), or null with a warn line. */
    internal fun core(method: String): NottiCore? = runtimeOrWarn(method)?.ensureCore()

    /**
     * The runtime the provider or [initialize] created. With none (provider removed and no
     * [initialize] yet) the call is dropped with a warn line naming only [method].
     */
    internal fun runtimeOrWarn(method: String): NottiRuntime? {
        val runtime = NottiRuntime.peek()
        if (runtime == null) {
            NottiLogger.warn("Notti.$method: called before initialize with no runtime - dropped")
            return null
        }
        wire(runtime)
        return runtime
    }

    private fun wire(runtime: NottiRuntime) {
        runtime.onDeviceIdChanged = deviceIdSlot
        runtime.onNotificationReceived = Notifications.receivedSlot
    }

    /** Test-only: drops every listener the facade holds (In-App clicks too) and detaches the click relay. */
    internal fun resetForTest() {
        deviceIdListeners.clearForTest()
        Notifications.resetForTest()
        NottiInAppClicks.listeners.clearForTest()
    }
}
