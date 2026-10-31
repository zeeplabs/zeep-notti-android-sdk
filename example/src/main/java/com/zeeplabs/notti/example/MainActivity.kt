package com.zeeplabs.notti.example

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.zeeplabs.notti.Notti
import com.zeeplabs.notti.NottiSubscription

/** The device id and the permission request; example-and-release adds one screen per public method. */
class MainActivity : ComponentActivity() {
    private var deviceIdSubscription: NottiSubscription? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deviceId = TextView(this)
        val permission = TextView(this)
        val request =
            Button(this).apply {
                text = "Request notification permission"
                setOnClickListener {
                    Notti.Notifications.requestPermission { granted -> permission.text = "Permission granted: $granted" }
                }
            }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(deviceId)
                addView(request)
                addView(permission)
            },
        )

        deviceId.text = "Device id: ${Notti.deviceId ?: "not registered"}"
        deviceIdSubscription = Notti.addDeviceIdChangedListener { id -> deviceId.text = "Device id: $id" }
    }

    override fun onDestroy() {
        deviceIdSubscription?.remove()
        super.onDestroy()
    }
}
