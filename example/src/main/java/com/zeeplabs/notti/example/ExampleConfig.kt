package com.zeeplabs.notti.example

import com.zeeplabs.notti.NottiOptions

/**
 * The Notti config from example/notti.properties (AD-016), compiled into BuildConfig. Incomplete when the
 * file is missing or still holds the sample placeholders; the app then never calls Notti.initialize.
 */
object ExampleConfig {
    private val placeholders = setOf("fixture-app-id", "fixture-client-key")

    val appId: String = BuildConfig.NOTTI_APP_ID
    val clientKey: String = BuildConfig.NOTTI_CLIENT_KEY

    val isComplete: Boolean = isComplete(appId, clientKey)

    val options: NottiOptions = NottiOptions(baseUrl = BuildConfig.NOTTI_BASE_URL.ifBlank { null })

    const val NOT_CONFIGURED = "Not configured: copy example/notti.properties.sample to example/notti.properties"

    fun isComplete(
        appId: String,
        clientKey: String,
    ): Boolean = listOf(appId, clientKey).all { it.isNotBlank() && it !in placeholders }
}
