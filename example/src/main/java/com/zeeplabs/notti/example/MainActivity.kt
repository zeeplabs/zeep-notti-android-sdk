package com.zeeplabs.notti.example

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.zeeplabs.notti.Notti
import com.zeeplabs.notti.NottiLogLevel
import com.zeeplabs.notti.NottiSubscription

/**
 * One screen with every public call of Notti, Notti.User, Notti.Notifications and Notti.InAppMessages
 * (EXR-02). Calls are written fully qualified (`Notti.User.setEmail(...)`, never `with(Notti.User)`):
 * scripts/check-example-coverage.sh finds them by name. Event log lines say which call ran, never the value
 * typed into a field (EXR-03).
 */
class MainActivity : ComponentActivity() {
    private var deviceIdSubscription: NottiSubscription? = null
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val padding = (16 * resources.displayMetrics.density).toInt()
        setContentView(ScrollView(this).apply { addView(root.apply { setPadding(padding, padding, padding, padding) }) })

        statusSection()
        permissionSection()
        identitySection()
        userSection()
        deviceSection()
        notificationsSection()
        inAppSection()
        eventLogSection()
    }

    override fun onDestroy() {
        deviceIdSubscription?.remove()
        ExampleEventLog.observe(null)
        super.onDestroy()
    }

    private fun statusSection() {
        section("Status")
        text(if (ExampleConfig.isComplete) "Configured" else ExampleConfig.NOT_CONFIGURED)
        val deviceId = text("Device id: ${Notti.deviceId ?: "not registered"}")
        deviceIdSubscription = Notti.addDeviceIdChangedListener { id -> deviceId.text = "Device id: $id" }

        text("Log level")
        val levels = NottiLogLevel.entries
        root.addView(
            Spinner(this).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, levels)
                setSelection(levels.indexOf(Notti.logLevel))
                onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long,
                        ) {
                            if (Notti.logLevel != levels[position]) {
                                Notti.logLevel = levels[position]
                                ExampleEventLog.add("log level: ${levels[position]}")
                            }
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    }
            },
        )
    }

    private fun permissionSection() {
        section("Permission")
        val result = text("Permission: not requested")
        button("Request notification permission") {
            Notti.Notifications.requestPermission { granted ->
                result.text = "Permission granted: $granted"
                ExampleEventLog.add("permission result: $granted")
            }
        }
    }

    private fun identitySection() {
        section("Identity")
        val externalId = field("External user id")
        button("Login") {
            Notti.login(externalId.text.toString())
            ExampleEventLog.add("login called")
        }
        button("Logout") {
            Notti.logout()
            ExampleEventLog.add("logout called")
        }
    }

    private fun userSection() {
        section("User")
        val email = field("Email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        button("Set email") {
            Notti.User.setEmail(email.text.toString())
            ExampleEventLog.add("setEmail called")
        }
        button("Clear email") {
            Notti.User.clearEmail()
            ExampleEventLog.add("clearEmail called")
        }
        val phone = field("Phone (E.164)", InputType.TYPE_CLASS_PHONE)
        button("Set phone") {
            Notti.User.setPhone(phone.text.toString())
            ExampleEventLog.add("setPhone called")
        }
        button("Clear phone") {
            Notti.User.clearPhone()
            ExampleEventLog.add("clearPhone called")
        }
        val tagKey = field("Tag key")
        val tagValue = field("Tag value")
        button("Add tag") {
            Notti.User.addTag(tagKey.text.toString(), tagValue.text.toString())
            ExampleEventLog.add("addTag called")
        }
        button("Remove tag") {
            Notti.User.removeTag(tagKey.text.toString())
            ExampleEventLog.add("removeTag called")
        }
        button("Add sample tags (plan, onboarding)") {
            Notti.User.addTags(mapOf("plan" to "fixture", "onboarding" to "done"))
            ExampleEventLog.add("addTags called")
        }
        button("Remove sample tags") {
            Notti.User.removeTags(listOf("plan", "onboarding"))
            ExampleEventLog.add("removeTags called")
        }
    }

    private fun deviceSection() {
        section("Device")
        // The SDK has no getters for these; the boxes start unchecked and only send what is tapped.
        root.addView(
            CheckBox(this).apply {
                text = "Subscribed to push"
                setOnCheckedChangeListener { _, checked ->
                    Notti.setSubscription(checked)
                    ExampleEventLog.add("setSubscription: $checked")
                }
            },
        )
        root.addView(
            CheckBox(this).apply {
                text = "Share location"
                setOnCheckedChangeListener { _, checked ->
                    Notti.setLocationSharingEnabled(checked)
                    ExampleEventLog.add("setLocationSharingEnabled: $checked")
                }
            },
        )
    }

    private fun notificationsSection() {
        section("Notifications")
        val initial = text("Initial click: not taken")
        button("Take initial click") {
            val click = Notti.Notifications.takeInitialClick()
            // Data keys only: values may carry personal data (EXR-03).
            initial.text = "Initial click: ${click?.data?.keys ?: "none"}"
            ExampleEventLog.add("takeInitialClick: ${if (click == null) "none" else "data keys ${click.data.keys}"}")
        }
    }

    private fun inAppSection() {
        section("In-App")
        val event = field("Event name").apply { setText("checkout_completed") }
        button("Track event") {
            Notti.trackEvent(event.text.toString())
            ExampleEventLog.add("trackEvent called")
        }
    }

    private fun eventLogSection() {
        section("Event log")
        val log = text("")
        ExampleEventLog.observe { lines -> log.text = lines.joinToString("\n") }
    }

    private fun section(title: String) {
        text(title).apply {
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
        }
    }

    private fun text(value: String): TextView = TextView(this).apply { text = value }.also(root::addView)

    private fun field(
        hint: String,
        type: Int = InputType.TYPE_CLASS_TEXT,
    ): EditText =
        EditText(this)
            .apply {
                this.hint = hint
                inputType = type
            }.also(root::addView)

    private fun button(
        label: String,
        onClick: () -> Unit,
    ) {
        root.addView(Button(this).apply { text = label }.also { it.setOnClickListener { onClick() } })
    }
}
