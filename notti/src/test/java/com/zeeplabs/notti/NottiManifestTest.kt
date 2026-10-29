package com.zeeplabs.notti

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The library manifest as merged into the test app (PUSH-01, PUSH-07), read through PackageManager. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiManifestTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val packageManager: PackageManager = app.packageManager

    @Test
    fun fcmServiceHandlesMessagingEventAndIsNotExported() {
        val intent = Intent("com.google.firebase.MESSAGING_EVENT").setPackage(app.packageName)

        val ours =
            packageManager
                .queryIntentServices(intent, 0)
                .map { it.serviceInfo }
                .filter { it.name == NottiFirebaseMessagingService::class.java.name }

        assertEquals(1, ours.size)
        assertFalse(ours.single().exported)
    }

    @Test
    fun initProviderHasItsOwnAuthorityAndIsNotExported() {
        val provider = packageManager.getProviderInfo(ComponentName(app, NottiInitProvider::class.java), 0)

        assertEquals("${app.packageName}.com.zeeplabs.notti.initprovider", provider.authority)
        assertNotEquals("${app.packageName}.nottiinitprovider", provider.authority)
        assertFalse(provider.exported)
        assertEquals(100, provider.initOrder)
    }

    @Test
    fun permissionActivityIsTranslucentHiddenAndNotExported() {
        val activity = packageManager.getActivityInfo(ComponentName(app, NottiPermissionActivity::class.java), 0)

        assertFalse(activity.exported)
        assertEquals(android.R.style.Theme_Translucent_NoTitleBar, activity.theme)
        assertTrue(activity.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0)
        // No noHistory: it could finish the Activity before the result while the system dialog is up.
        assertEquals(0, activity.flags and ActivityInfo.FLAG_NO_HISTORY)
    }

    @Test
    fun postNotificationsIsRequested() {
        val info = packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)

        assertTrue(info.requestedPermissions.orEmpty().contains(Manifest.permission.POST_NOTIFICATIONS))
    }
}
