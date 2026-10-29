// Ported from react-native-notti v0.8.0 (291d5a7): android/src/test/java/com/notti/NottiBridgeTest.kt
@file:Suppress(
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:no-empty-first-line-in-class-body",
)

package com.zeeplabs.notti

import android.os.Bundle
import com.google.firebase.messaging.RemoteMessage
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * NottiBridge is the manual-forwarding counterpart to the automatic path
 * NottiModuleTest/NottiFirebaseMessagingServiceTest already cover; this
 * only proves the public entry points reach the same active module/core
 * without throwing, since a real NottiModule must be active either way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NottiBridgeTest {

    @Before
    @After
    fun resetProcessWideState() {
        NottiRuntime.resetForTest()
    }

    @Test
    fun `onMessageReceived is a safe no-op when no module is active`() {
        // RN's emit-to-JS limitation does not apply here (the SDK has no
        // bridge to JS) -- this only proves onMessageReceived's own null-guard
        // with no live core; NottiBridgeRuntimeTest covers the full path.
        val runtime = NottiRuntime.get(RuntimeEnvironment.getApplication())
        val core = runtime.start("app-1", "key", "https://notti.example.com")
        runtime.stop(core)
        assertNull(NottiRuntime.peek()?.core)

        val bundle = Bundle().apply {
            putString("gcm.n.title", "Hello")
            putString("gcm.n.body", "World")
        }

        NottiBridge.onMessageReceived(RemoteMessage(bundle))
    }

    @Test
    fun `onNewToken reaches the active core without throwing`() {
        val runtime = NottiRuntime.get(RuntimeEnvironment.getApplication())
        runtime.start("app-1", "key", "https://notti.example.com")
        assertNotNull(NottiRuntime.peek()?.core)

        NottiBridge.onNewToken("new-token-value")
    }
}
