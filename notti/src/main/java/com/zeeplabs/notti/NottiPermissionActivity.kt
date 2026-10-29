package com.zeeplabs.notti

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle

/**
 * Invisible host of the `POST_NOTIFICATIONS` system dialog, started by [NottiPermissionRequester]
 * (translucent, not exported, declared in the library manifest). It asks once for the request id in its
 * Intent, hands the answer to [NottiPermissionRequester.resolve] and finishes with no animation.
 *
 * Recreated while the dialog is up (saved state): it does not ask again and waits for the answer the
 * system delivers to the new instance. Recreated after the answer or after process death (the id is no
 * longer pending), or started with no id: it finishes without asking.
 */
internal class NottiPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        val id = intent?.getIntExtra(NottiPermissionRequester.EXTRA_REQUEST_ID, NO_ID) ?: NO_ID
        when {
            id == NO_ID || !NottiPermissionRequester.isPending(id) -> {
                finishWithoutAnimation()
            }

            savedInstanceState != null -> {
                Unit
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), id)
            }

            else -> {
                // Not started below Android 13 by the requester; answer as RN does there.
                NottiPermissionRequester.resolve(id, true)
                finishWithoutAnimation()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        NottiPermissionRequester.resolve(requestCode, granted)
        finishWithoutAnimation()
    }

    private fun finishWithoutAnimation() {
        finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private companion object {
        const val NO_ID = -1
    }
}
