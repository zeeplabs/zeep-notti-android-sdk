package com.zeeplabs.notti.example.manualfcm

import android.app.Application
import android.util.Log
import com.zeeplabs.notti.Notti
import com.zeeplabs.notti.NottiOptions

/** Initializes Notti from example/notti.properties (AD-016), shared with :example; no screen. */
class ManualFcmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val appId = BuildConfig.NOTTI_APP_ID
        val clientKey = BuildConfig.NOTTI_CLIENT_KEY
        if (listOf(appId, clientKey).any { it.isBlank() || it in PLACEHOLDERS }) {
            Log.w(TAG, "Not configured: copy example/notti.properties.sample to example/notti.properties")
            return
        }
        Notti.initialize(this, appId, clientKey, NottiOptions(baseUrl = BuildConfig.NOTTI_BASE_URL.ifBlank { null }))
    }

    private companion object {
        const val TAG = "NottiManualFcm"
        val PLACEHOLDERS = setOf("fixture-app-id", "fixture-client-key")
    }
}
