package com.zeeplabs.notti.example

import android.app.Application
import com.zeeplabs.notti.Notti
import com.zeeplabs.notti.NottiLogLevel

/**
 * Initializes Notti at process start. The listeners are added before initialize, so the tap that launched
 * the app (cold start) and an In-App message shown right after launch are both seen.
 */
class ExampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notti.logLevel = NottiLogLevel.DEBUG
        // Data keys only: values may carry personal data (EXR-03).
        Notti.Notifications.addReceivedListener { notification ->
            ExampleEventLog.add("notification received, data keys: ${notification.data.keys}")
        }
        Notti.Notifications.addClickedListener { notification ->
            ExampleEventLog.add("notification tapped, data keys: ${notification.data.keys}")
        }
        // Ids and the action type only: a link may carry personal data.
        Notti.InAppMessages.addClickListener { click ->
            ExampleEventLog.add(
                "in-app tap, message: ${click.messageId}, button: ${click.buttonId}, action: ${click.action?.type}",
            )
        }
        // Without example/notti.properties the app shows "not configured" and never initializes; the other
        // controls still run and the SDK drops them. Without google-services.json the SDK logs why it cannot
        // register and the app keeps running.
        if (ExampleConfig.isComplete) {
            Notti.initialize(this, ExampleConfig.appId, ExampleConfig.clientKey, ExampleConfig.options)
            ExampleEventLog.add("initialize called")
        } else {
            ExampleEventLog.add(ExampleConfig.NOT_CONFIGURED)
        }
    }

    companion object {
        const val TAG = "NottiExample"
    }
}
