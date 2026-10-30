package com.zeeplabs.notti

import android.app.Application
import android.content.Context

/**
 * The Notti SDK: push registration, user identity and profile, notification events.
 *
 * Call [initialize] once, as early as possible (ideally in `Application.onCreate`). Every other call
 * is safe before it: the SDK keeps what it can and sends it after the first registration. Nothing
 * here throws; invalid input is logged and ignored. Listeners and callbacks run on the main thread.
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
     * the device registers again with the new values. Blank ids or an invalid base URL are logged and
     * ignored, and a previous valid configuration stays in effect.
     */
    @JvmStatic
    @JvmOverloads
    fun initialize(
        context: Context,
        appId: String,
        clientKey: String,
        options: NottiOptions = NottiOptions(),
    ) {
        try {
            val app = context.applicationContext ?: context
            if (app is Application) {
                NottiProcessHooks.installOnce(app)
            } else {
                NottiLogger.error("Notti.initialize: no Application context - process hooks not installed")
            }
            val runtime = NottiRuntime.get(app)
            wire(runtime)
            runtime.start(appId, clientKey, options.baseUrl ?: DEFAULT_BASE_URL)
        } catch (t: Throwable) {
            NottiLogger.error("Notti.initialize: failed - ${t.javaClass.simpleName}")
        }
    }

    /** Links this device to your user id; sent after the first registration if called before it. */
    @JvmStatic
    fun login(externalUserId: String) {
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
            key: String,
            value: String,
        ) {
            core("User.addTag")?.mutateTags(add = mapOf(key to value), remove = null)
        }

        /** Sets several tags in one request. */
        @JvmStatic
        fun addTags(tags: Map<String, String>) {
            core("User.addTags")?.mutateTags(add = tags.toMap(), remove = null)
        }

        /** Removes one tag. */
        @JvmStatic
        fun removeTag(key: String) {
            core("User.removeTag")?.mutateTags(add = null, remove = listOf(key))
        }

        /** Removes several tags in one request. */
        @JvmStatic
        fun removeTags(keys: List<String>) {
            core("User.removeTags")?.mutateTags(add = null, remove = keys.toList())
        }

        @JvmStatic
        fun setEmail(email: String) {
            core("User.setEmail")?.setEmail(email)
        }

        @JvmStatic
        fun clearEmail() {
            core("User.clearEmail")?.clearEmail()
        }

        @JvmStatic
        fun setPhone(phone: String) {
            core("User.setPhone")?.setPhone(phone)
        }

        @JvmStatic
        fun clearPhone() {
            core("User.clearPhone")?.clearPhone()
        }
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
    }

    /** Test-only: drops every listener the facade holds. */
    internal fun resetForTest() {
        deviceIdListeners.clearForTest()
    }
}
