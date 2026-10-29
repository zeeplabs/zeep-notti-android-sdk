package com.zeeplabs.notti

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * The Android readers [NottiCore] takes as providers (CORE-06..08). Each body is moved from RN
 * v0.8.0 `NottiModule.kt` (`readAppVersion` … `fetchFcmToken`, lines 336-516) with
 * `reactApplicationContext` → [context], `reactApplicationContext.currentActivity` →
 * [currentActivity] and the log lines routed through [NottiLogger] with the exception class
 * (AD-011). This is a new file, so the moved bodies are formatted to ktlint style; the logic is
 * unchanged.
 *
 * Seams for tests (not in RN): [geocoderFactory] (Robolectric shadows a Geocoder instance, not
 * its constructor) and [firebaseToken] (drives success, failure and a throwing `getInstance()`).
 */
internal class NottiAndroidProviders(
    private val context: Context,
    private val deviceStore: NottiDeviceStore,
    private val currentActivity: () -> Activity?,
    private val geoExecutor: Executor,
    private val geocoderFactory: (Context) -> Geocoder = { Geocoder(it, Locale.US) },
    private val firebaseToken: () -> Task<String> = { FirebaseMessaging.getInstance().token },
) {
    /**
     * Reads the host app's version string once per registration (SEGTEL-01).
     * `PackageInfo.versionName` can throw if the package is absent; the SDK
     * must never crash here, so any failure resolves `null` - the sync then
     * skips entirely (SEGTEL edge case).
     */
    fun appVersion(): String? =
        try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
        } catch (t: Throwable) {
            NottiLogger.error("Notti: failed to read app version - ${t.javaClass.simpleName}")
            null
        }

    /**
     * Reads the OS version string (device-profile-fields DPF-01). `Build` reads
     * are plain static field accesses that cannot throw in practice, but the
     * wrap keeps the "never crash on a read failure" contract (DPF-04) uniform.
     */
    fun deviceOs(): String? =
        try {
            Build.VERSION.RELEASE
        } catch (t: Throwable) {
            NottiLogger.error("Notti: failed to read OS version - ${t.javaClass.simpleName}")
            null
        }

    /** Reads the device model (DPF-01); see [deviceOs] for the failure contract. */
    fun deviceModel(): String? =
        try {
            Build.MODEL
        } catch (t: Throwable) {
            NottiLogger.error("Notti: failed to read device model - ${t.javaClass.simpleName}")
            null
        }

    /** Reads the IANA timezone id (DPF-06); failure resolves `null` (DPF-09). */
    fun timezoneId(): String? =
        try {
            java.util.TimeZone
                .getDefault()
                .id
        } catch (t: Throwable) {
            NottiLogger.error("Notti: failed to read timezone - ${t.javaClass.simpleName}")
            null
        }

    /**
     * Reads the OS language ISO 639-1 code (DPF-06) via
     * [NottiCore.normalizeLanguage] (primary subtag, legacy codes mapped,
     * `und`/empty -> `null`); failure resolves `null` (DPF-09).
     */
    fun language(): String? =
        try {
            NottiCore.normalizeLanguage(Locale.getDefault().toLanguageTag())
        } catch (t: Throwable) {
            NottiLogger.error("Notti: failed to read language - ${t.javaClass.simpleName}")
            null
        }

    /**
     * Best-effort OS push-permission state read (DPF-10): gathers the raw OS
     * signals and maps them through [NottiCore.mapPermissionStatus] (the
     * branch table and its known limitation are documented there). The
     * rationale signal needs an Activity; with none in the foreground it is
     * passed as `null`, which makes a never-requested, ungranted permission
     * resolve `null` (can't tell `notDetermined` from denied elsewhere) instead
     * of a guess. Any failure resolves `null` (omit - never fabricate).
     * `InlinedApi` is suppressed: `POST_NOTIFICATIONS` is read only in the API 33+ branch,
     * which lint cannot see through the `sdkInt` local.
     */
    @SuppressLint("InlinedApi")
    fun permissionStatus(callback: (String?) -> Unit) {
        val status =
            try {
                val notificationsEnabled =
                    NotificationManagerCompat
                        .from(context)
                        .areNotificationsEnabled()
                val sdkInt = Build.VERSION.SDK_INT
                if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
                    NottiCore.mapPermissionStatus(sdkInt, notificationsEnabled, false, false, null)
                } else {
                    val permissionGranted =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) == PackageManager.PERMISSION_GRANTED
                    val shouldShowRationale =
                        currentActivity()?.let {
                            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
                        }
                    NottiCore.mapPermissionStatus(
                        sdkInt,
                        notificationsEnabled,
                        permissionGranted,
                        deviceStore.getPermissionRequested(),
                        shouldShowRationale,
                    )
                }
            } catch (t: Throwable) {
                NottiLogger.error("Notti: failed to read permission status - ${t.javaClass.simpleName}")
                null
            }
        callback(status)
    }

    /**
     * Check-only location permission gate (SEGTEL-14): true only when the host
     * app has already been granted the permission. Never prompts - the SDK only
     * reads, it never requests OS location permission. Either permission is
     * sufficient for the country-level read (a host may declare only
     * `ACCESS_FINE_LOCATION`).
     */
    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * Best-effort country resolution (SEGTEL-11): reads the last known location
     * (may be a stale cached fix - acceptable for country-level granularity) and
     * reverse-geocodes it to an ISO 3166-1 alpha-2 `countryCode`. Geocoding is a
     * blocking network call, so it runs on [geoExecutor]. Any failure
     * (location unavailable, services disabled, no fix, geocode error) resolves
     * `null` - treated the same as no permission: omit, no error, no prompt.
     * `MissingPermission` is suppressed: the core calls this only after [hasLocationPermission],
     * and the location read catches a `SecurityException`.
     */
    @SuppressLint("MissingPermission")
    fun country(callback: (String?) -> Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val location =
            try {
                locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } catch (t: Throwable) {
                NottiLogger.error("Notti: failed to read last known location - ${t.javaClass.simpleName}")
                null
            }
        if (location == null) {
            callback(null)
            return
        }
        try {
            geoExecutor.execute {
                try {
                    val geocoder = geocoderFactory(context)

                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    callback(addresses?.firstOrNull()?.countryCode)
                } catch (t: Throwable) {
                    NottiLogger.error("Notti: failed to reverse-geocode location - ${t.javaClass.simpleName}")
                    callback(null)
                }
            }
        } catch (e: RejectedExecutionException) {
            // Runtime torn down: no country this time (treated as omit, SEGTEL-14).
            callback(null)
        }
    }

    /**
     * Fetches the current FCM token via `FirebaseMessaging.getInstance().token`
     * (Play Services Tasks API, asynchronous). Mirrors SDK-04's crash-safety
     * contract for a missing native push prerequisite (no Firebase app
     * configured / no `google-services.json`): any failure - synchronous
     * (Firebase not initialized) or asynchronous (`addOnFailureListener`) - is
     * logged and resolves the callback with `null`, never throws.
     *
     * Both listeners run on [executor] (the core's `notti-io`, passed per call because the
     * runtime creates one per core): the no-executor overloads run on the main looper, which
     * would put the whole registration path (blocking OkHttp call + retry backoff) on the UI
     * thread.
     */
    fun fcmToken(
        executor: Executor,
        callback: (String?) -> Unit,
    ) {
        try {
            firebaseToken()
                .addOnSuccessListener(executor) { token -> callback(token) }
                .addOnFailureListener(executor) { e ->
                    NottiLogger.error("Notti: failed to fetch FCM token - ${e.javaClass.simpleName}")
                    callback(null)
                }
        } catch (e: Exception) {
            NottiLogger.error("Notti: failed to fetch FCM token - ${e.javaClass.simpleName}")
            callback(null)
        }
    }
}
