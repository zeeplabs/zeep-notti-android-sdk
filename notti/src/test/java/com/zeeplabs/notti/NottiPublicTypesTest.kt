package com.zeeplabs.notti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The public value types (API-10, API-12, API-14, API-15; In-App design D6). */
class NottiPublicTypesTest {
    private val sentinelData = mapOf("email" to "sentinel@example.invalid", "plan" to "sentinel-plan")

    @Test
    fun notificationToStringPrintsDataKeysOnly() {
        val notification = NottiNotification("Sentinel title", "Sentinel body", sentinelData)

        val text = notification.toString()

        assertEquals("NottiNotification(hasTitle=true, hasBody=true, dataKeys=[email, plan])", text)
        assertFalse(text.contains("sentinel", ignoreCase = true))
    }

    @Test
    fun notificationEqualityIsByValue() {
        val a = NottiNotification("t", null, mapOf("k" to "v"))

        assertEquals(a, NottiNotification("t", null, mapOf("k" to "v")))
        assertEquals(a.hashCode(), NottiNotification("t", null, mapOf("k" to "v")).hashCode())
        assertNotEquals(a, NottiNotification("t", null, mapOf("k" to "other")))
        assertNotEquals(a, NottiNotification(null, null, mapOf("k" to "v")))
    }

    @Test
    fun toPublicKeepsTheDataExactly() {
        val parsed = ParsedNotification("t", "b", mapOf("notification_id" to "n-fixture", "url" to "https://example.invalid/x"))

        val public = parsed.toPublic()

        assertEquals("t", public.title)
        assertEquals("b", public.body)
        assertEquals(parsed.data, public.data)
    }

    @Test
    fun logLevelMapsAllSixValuesBothWays() {
        assertEquals(6, NottiLogLevel.entries.size)
        NottiLogLevel.entries.forEach { level ->
            assertEquals(level.name, level.toLoggerLevel().name)
            assertEquals(level, level.toLoggerLevel().toPublic())
        }
        NottiLogger.Level.entries.forEach { assertEquals(it, it.toPublic().toLoggerLevel()) }
    }

    @Test
    fun optionsDefaultToNoBaseUrl() {
        assertNull(NottiOptions().baseUrl)
        assertEquals("https://push.example.invalid", NottiOptions("https://push.example.invalid").baseUrl)
        assertEquals(NottiOptions(), NottiOptions(null))
    }

    @Test
    fun optionsOpenInAppUrlsByDefaultAndCompareByEveryField() {
        assertTrue(NottiOptions().opensInAppUrls)
        assertTrue(NottiOptions("https://push.example.invalid").opensInAppUrls)
        assertFalse(NottiOptions(null, false).opensInAppUrls)
        assertNotEquals(NottiOptions(), NottiOptions(null, false))
        assertEquals(NottiOptions(null, false).hashCode(), NottiOptions(null, false).hashCode())
        assertEquals("NottiOptions(baseUrl=null, opensInAppUrls=false)", NottiOptions(null, false).toString())
    }

    @Test
    fun subscriptionRemoveRunsOnce() {
        var removals = 0
        val subscription = NottiSubscription { removals++ }

        subscription.remove()
        subscription.remove()

        assertEquals(1, removals)
    }

    @Test
    fun inAppClickToStringHidesTheMessageIdAndTheLink() {
        val click =
            NottiInAppClick(
                "sentinel-message",
                "b1",
                NottiInAppAction(NottiInAppAction.Type.URL, "https://sentinel.example.invalid/?email=sentinel"),
            )

        val text = click.toString() + click.action.toString()

        assertEquals("NottiInAppClick(buttonId=b1, actionType=URL)", click.toString())
        assertEquals("NottiInAppAction(type=URL)", click.action.toString())
        assertFalse(text.contains("sentinel", ignoreCase = true))
        assertEquals("NottiInAppClick(buttonId=image, actionType=null)", NottiInAppClick("m", "image", null).toString())
    }

    @Test
    fun inAppClickEqualityIsByValue() {
        val action = NottiInAppAction(NottiInAppAction.Type.DEEPLINK, "app://fixture")
        val a = NottiInAppClick("m", "b1", action)

        assertEquals(a, NottiInAppClick("m", "b1", NottiInAppAction(NottiInAppAction.Type.DEEPLINK, "app://fixture")))
        assertEquals(a.hashCode(), NottiInAppClick("m", "b1", action).hashCode())
        assertNotEquals(a, NottiInAppClick("m", "b2", action))
        assertNotEquals(a, NottiInAppClick("m", "b1", null))
        assertNotEquals(action, NottiInAppAction(NottiInAppAction.Type.URL, "app://fixture"))
    }

    @Test
    fun inAppToPublicMapsUrlAndDeeplinkAndDropsPushPermission() {
        assertEquals(
            NottiInAppClick("m", "b1", NottiInAppAction(NottiInAppAction.Type.URL, "https://example.invalid")),
            InAppClick("m", "b1", InAppAction("url", "https://example.invalid")).toPublic(),
        )
        assertEquals(
            NottiInAppAction(NottiInAppAction.Type.DEEPLINK, "app://fixture"),
            InAppAction("deeplink", "app://fixture").toPublic(),
        )
        assertNull(InAppAction(InAppAction.PUSH_PERMISSION, "").toPublic())
        assertNull(InAppClick("m", "image", null).toPublic().action)
    }
}
