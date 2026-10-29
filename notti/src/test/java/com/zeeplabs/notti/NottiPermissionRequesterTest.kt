package com.zeeplabs.notti

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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

/**
 * [NottiPermissionRequester] and [NottiPermissionActivity] (PUSH-16..18): the dialog is hosted by the
 * SDK's Activity, started from the current host Activity, and answers come back through
 * `onRequestPermissionsResult` as Robolectric delivers them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiPermissionRequesterTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private lateinit var deviceStore: NottiDeviceStore

    @Before
    fun setUp() {
        NottiPermissionRequester.resetForTest()
        val prefs = app.getSharedPreferences("notti_permission_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        deviceStore = NottiDeviceStore(prefs)
    }

    @After
    fun tearDown() {
        NottiPermissionRequester.resetForTest()
    }

    private fun hostActivity(): Activity =
        Robolectric
            .buildActivity(Activity::class.java)
            .create()
            .start()
            .resume()
            .get()

    private fun requester(activity: () -> Activity?): NottiPermissionRequester = NottiPermissionRequester(app, deviceStore, activity)

    /** The next Intent the host was asked to start; Robolectric queues started Intents process-wide. */
    private fun startedPermissionIntent(host: Activity): Intent {
        val intent = shadowOf(host).nextStartedActivity
        assertNotNull("no Activity was started", intent)
        assertEquals(NottiPermissionActivity::class.java.name, intent.component?.className)
        return intent
    }

    /** The permission Activity created from [intent] as the system would. */
    private fun permissionActivity(intent: Intent): NottiPermissionActivity =
        Robolectric.buildActivity(NottiPermissionActivity::class.java, intent).create().get()

    private fun launchedPermissionActivity(host: Activity): NottiPermissionActivity = permissionActivity(startedPermissionIntent(host))

    private fun answer(
        activity: NottiPermissionActivity,
        granted: Boolean,
    ) {
        val request = shadowOf(activity).lastRequestedPermission
        assertNotNull("the dialog was not requested", request)
        val result = if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(result))
    }

    @Test
    @Config(sdk = [32])
    fun `requestPermission on Android below 13 resolves granted without ever touching the current activity`() {
        // Ported from RN NottiModuleTest (SDK-11, P2-AC4); RN went through the module, the SDK has the requester.
        val results = mutableListOf<Boolean>()

        requester { throw AssertionError("the current Activity was read below API 33") }.request { results += it }

        assertEquals(listOf(true), results)
        assertFalse(deviceStore.getPermissionRequested())
    }

    @Test
    fun noActivityResolvesFalseWithoutSettingTheFlag() {
        val results = mutableListOf<Boolean>()

        requester { null }.request { results += it }

        assertEquals(listOf(false), results)
        assertFalse(deviceStore.getPermissionRequested())
    }

    @Test
    fun grantedAnswerResolvesTrueOnceAndFinishes() {
        val host = hostActivity()
        val results = mutableListOf<Boolean>()
        requester { host }.request { results += it }

        val dialog = launchedPermissionActivity(host)
        val request = shadowOf(dialog).lastRequestedPermission
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), request.requestedPermissions.toList())
        answer(dialog, granted = true)
        answer(dialog, granted = false)

        assertEquals(listOf(true), results)
        assertTrue(dialog.isFinishing)
    }

    @Test
    fun deniedAnswerResolvesFalse() {
        val host = hostActivity()
        val results = mutableListOf<Boolean>()
        requester { host }.request { results += it }

        answer(launchedPermissionActivity(host), granted = false)

        assertEquals(listOf(false), results)
    }

    @Test
    fun flagIsSetBeforeTheDialogIsAnswered() {
        val host = hostActivity()
        val results = mutableListOf<Boolean>()

        requester { host }.request { results += it }

        assertTrue(deviceStore.getPermissionRequested())
        assertEquals(emptyList<Boolean>(), results)
    }

    @Test
    fun twoConcurrentRequestsEachResolveOnceWithTheirOwnAnswer() {
        val host = hostActivity()
        val first = mutableListOf<Boolean>()
        val second = mutableListOf<Boolean>()
        val requester = requester { host }
        requester.request { first += it }
        requester.request { second += it }
        // Matched by request id: the order Robolectric hands started Intents back in is not the start order.
        val (firstIntent, secondIntent) =
            listOf(startedPermissionIntent(host), startedPermissionIntent(host))
                .sortedBy { it.getIntExtra(NottiPermissionRequester.EXTRA_REQUEST_ID, -1) }
        val firstDialog = permissionActivity(firstIntent)
        val secondDialog = permissionActivity(secondIntent)

        answer(secondDialog, granted = true)
        answer(firstDialog, granted = false)

        assertEquals(listOf(false), first)
        assertEquals(listOf(true), second)
    }

    @Test
    fun startActivityFailureResolvesFalse() {
        val results = mutableListOf<Boolean>()
        val host =
            object : Activity() {
                override fun startActivity(intent: Intent?): Unit = throw ActivityNotFoundException()
            }

        requester { host }.request { results += it }

        assertEquals(listOf(false), results)
        assertFalse(NottiPermissionRequester.isPending(1))
    }

    @Test
    fun recreatedWhileTheDialogIsUpDoesNotAskAgainAndStillResolves() {
        val host = hostActivity()
        val results = mutableListOf<Boolean>()
        requester { host }.request { results += it }
        val intent = shadowOf(host).nextStartedActivity

        val recreated = Robolectric.buildActivity(NottiPermissionActivity::class.java, intent).create(Bundle()).get()

        assertNull(shadowOf(recreated).lastRequestedPermission)
        assertFalse(recreated.isFinishing)
        recreated.onRequestPermissionsResult(
            1,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            intArrayOf(PackageManager.PERMISSION_GRANTED),
        )
        assertEquals(listOf(true), results)
        assertTrue(recreated.isFinishing)
    }

    @Test
    fun anIdNoLongerPendingFinishesWithoutAsking() {
        val intent = Intent(app, NottiPermissionActivity::class.java).putExtra(NottiPermissionRequester.EXTRA_REQUEST_ID, 42)

        val activity = Robolectric.buildActivity(NottiPermissionActivity::class.java, intent).create().get()

        assertNull(shadowOf(activity).lastRequestedPermission)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun noIdFinishesWithoutAsking() {
        val activity = Robolectric.buildActivity(NottiPermissionActivity::class.java).create().get()

        assertNull(shadowOf(activity).lastRequestedPermission)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun theStartedIntentHasNoAnimation() {
        val host = hostActivity()
        requester { host }.request { }

        val intent = shadowOf(host).nextStartedActivity

        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION != 0)
        assertEquals(1, intent.getIntExtra(NottiPermissionRequester.EXTRA_REQUEST_ID, -1))
    }
}
