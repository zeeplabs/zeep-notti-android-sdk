package com.zeeplabs.notti

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import com.google.android.gms.tasks.Tasks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Every reader of [NottiAndroidProviders] (CORE-06..08, CORE-11), each with its failure → null
 * path where the platform can fail. `deviceOs`, `deviceModel`, `timezoneId` and `language` read
 * static fields that cannot throw under test, so only their success path is asserted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiAndroidProvidersTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val direct = Executor { it.run() }
    private lateinit var deviceStore: NottiDeviceStore
    private lateinit var savedLocale: Locale
    private lateinit var savedZone: TimeZone

    @Before
    fun setUp() {
        deviceStore = NottiDeviceStore(app.getSharedPreferences("notti_providers_test", Context.MODE_PRIVATE))
        savedLocale = Locale.getDefault()
        savedZone = TimeZone.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(savedLocale)
        TimeZone.setDefault(savedZone)
    }

    private fun providers(
        context: Context = app,
        activity: Activity? = null,
        geoExecutor: Executor = direct,
        geocoder: (Context) -> Geocoder = { Geocoder(it, Locale.US) },
        firebaseToken: (() -> com.google.android.gms.tasks.Task<String>)? = null,
    ): NottiAndroidProviders =
        if (firebaseToken == null) {
            NottiAndroidProviders(context, deviceStore, { activity }, geoExecutor, geocoder)
        } else {
            NottiAndroidProviders(context, deviceStore, { activity }, geoExecutor, geocoder, firebaseToken)
        }

    private fun permissionStatus(p: NottiAndroidProviders): String? {
        var result: String? = "unset"
        p.permissionStatus { result = it }
        return result
    }

    private fun setNotificationsEnabled(enabled: Boolean) {
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(enabled)
    }

    // --- static readers ---

    @Test
    fun appVersionReadsVersionName() {
        shadowOf(app.packageManager).getInternalMutablePackageInfo(app.packageName).versionName = "9.9.9-fixture"
        assertEquals("9.9.9-fixture", providers().appVersion())
    }

    @Test
    fun appVersionMissingPackageIsNull() {
        val missing =
            object : ContextWrapper(app) {
                override fun getPackageName(): String = "invalid.fixture.missing"
            }
        assertNull(providers(context = missing).appVersion())
    }

    @Test
    fun deviceOsAndModelReadBuild() {
        assertEquals(android.os.Build.VERSION.RELEASE, providers().deviceOs())
        assertEquals(android.os.Build.MODEL, providers().deviceModel())
    }

    @Test
    fun timezoneIdReadsDefaultZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
        assertEquals("America/Sao_Paulo", providers().timezoneId())
    }

    @Test
    fun languageIsPrimarySubtag() {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))
        assertEquals("pt", providers().language())
    }

    @Test
    fun languageUndeterminedIsNull() {
        Locale.setDefault(Locale.ROOT)
        assertNull(providers().language())
    }

    // --- permission status (DPF-10) ---

    @Test
    @Config(sdk = [32])
    fun permissionStatusApi32Enabled() {
        setNotificationsEnabled(true)
        assertEquals("granted", permissionStatus(providers()))
    }

    @Test
    @Config(sdk = [32])
    fun permissionStatusApi32Disabled() {
        setNotificationsEnabled(false)
        assertEquals("denied", permissionStatus(providers()))
    }

    @Test
    fun permissionStatusApi34GrantedAndEnabled() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        setNotificationsEnabled(true)
        assertEquals("granted", permissionStatus(providers()))
    }

    @Test
    fun permissionStatusApi34GrantedButDisabled() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        setNotificationsEnabled(false)
        assertEquals("denied", permissionStatus(providers()))
    }

    @Test
    fun permissionStatusApi34NotGrantedNoActivityIsNull() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertNull(permissionStatus(providers(activity = null)))
    }

    @Test
    fun permissionStatusApi34NotGrantedRationaleTrueIsDenied() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true)
        assertEquals("denied", permissionStatus(providers(activity = activity)))
    }

    @Test
    fun permissionStatusApi34NotGrantedRationaleFalseIsNotDetermined() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, false)
        assertEquals("notDetermined", permissionStatus(providers(activity = activity)))
    }

    @Test
    fun permissionStatusApi34RequestedBeforeIsDenied() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        deviceStore.setPermissionRequested(true)
        assertEquals("denied", permissionStatus(providers(activity = null)))
    }

    @Test
    fun permissionStatusReadFailureIsNull() {
        // Granted and enabled: without the failure this would read "granted".
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        setNotificationsEnabled(true)
        val broken =
            object : ContextWrapper(app) {
                override fun getSystemService(name: String): Any? = throw IllegalStateException("fixture")

                override fun getApplicationContext(): Context = this
            }
        assertNull(permissionStatus(providers(context = broken)))
    }

    // --- location (SEGTEL-14) ---

    @Test
    fun locationCoarseOnlyIsEnough() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertTrue(providers().hasLocationPermission())
    }

    @Test
    fun locationFineOnlyIsEnough() {
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertTrue(providers().hasLocationPermission())
    }

    @Test
    fun locationNoneIsFalse() {
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        assertFalse(providers().hasLocationPermission())
    }

    // --- country (SEGTEL-11) ---

    private fun seedLastLocation() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        val location =
            Location(LocationManager.NETWORK_PROVIDER).apply {
                latitude = -23.5
                longitude = -46.6
                time = 1L
            }
        shadowOf(app.getSystemService(LocationManager::class.java))
            .setLastKnownLocation(LocationManager.NETWORK_PROVIDER, location)
    }

    private fun country(p: NottiAndroidProviders): String? {
        var result: String? = "unset"
        p.country { result = it }
        return result
    }

    @Test
    fun countryResolvesCountryCode() {
        seedLastLocation()
        val geocoder =
            Geocoder(app, Locale.US).also {
                shadowOf(it).setFromLocation(listOf(Address(Locale.US).apply { countryCode = "BR" }))
            }
        assertEquals("BR", country(providers(geocoder = { geocoder })))
    }

    @Test
    fun countryWithoutLastLocationIsNull() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        assertNull(country(providers()))
    }

    @Test
    fun countryGeocoderFailureIsNull() {
        seedLastLocation()
        assertNull(country(providers(geocoder = { throw IOException("fixture") })))
    }

    @Test
    fun countryWithShutExecutorIsNull() {
        seedLastLocation()
        val shut = Executors.newSingleThreadExecutor().apply { shutdown() }
        assertNull(country(providers(geoExecutor = shut)))
    }

    // --- FCM token (SDK-04) ---

    private fun token(p: NottiAndroidProviders): String? {
        var result: String? = "unset"
        p.fcmToken(direct) { result = it }
        return result
    }

    @Test
    fun fcmTokenSuccess() {
        assertEquals("fixture-token", token(providers(firebaseToken = { Tasks.forResult("fixture-token") })))
    }

    @Test
    fun fcmTokenFailureIsNull() {
        assertNull(token(providers(firebaseToken = { Tasks.forException(IOException("fixture")) })))
    }

    @Test
    fun fcmTokenThrowingSeamIsNull() {
        assertNull(token(providers(firebaseToken = { throw IllegalStateException("fixture") })))
    }

    @Test
    fun fcmTokenWithoutFirebaseAppIsNullNoCrash() {
        // Default seam: FirebaseMessaging.getInstance() throws when no FirebaseApp is configured.
        var called = false
        var result: String? = "unset"
        providers().fcmToken(direct) {
            called = true
            result = it
        }
        assertTrue(called)
        assertNull(result)
    }

    @Test
    fun readFailuresLogTheExceptionClassOnly() {
        val lines = mutableListOf<String>()
        NottiLogger.sink = { _, message -> lines += message }
        try {
            token(providers(firebaseToken = { Tasks.forException(IOException("https://example.invalid/secret")) }))
        } finally {
            NottiLogger.sink = NottiLogger.defaultSink
        }
        assertNotNull(lines.singleOrNull())
        assertEquals("Notti: failed to fetch FCM token - IOException", lines.single())
    }
}
