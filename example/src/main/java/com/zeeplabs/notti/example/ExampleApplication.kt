package com.zeeplabs.notti.example

import android.app.Application
import android.util.Log
import com.zeeplabs.notti.Notti
import com.zeeplabs.notti.NottiLogLevel

/**
 * Initializes Notti at process start, so the tap that launched the app arrives as a click event and an
 * In-App message shown right after launch already has a click listener.
 */
class ExampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notti.logLevel = NottiLogLevel.DEBUG
        Notti.Notifications.addClickedListener { notification ->
            // Data keys only: values may carry personal data.
            Log.i(TAG, "notification tapped, data keys: ${notification.data.keys}")
        }
        Notti.InAppMessages.addClickListener { click ->
            // The button id and the action type only: a link may carry personal data.
            Log.i(TAG, "in-app tap, button: ${click.buttonId}, action: ${click.action?.type}")
        }
        // Placeholders: without a real app id, client key and google-services.json the SDK logs
        // why it cannot register and the app keeps running.
        Notti.initialize(this, "YOUR_APP_ID", "YOUR_CLIENT_KEY")
    }

    companion object {
        const val TAG = "NottiExample"
    }
}
