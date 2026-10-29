package com.zeeplabs.notti

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Proves the real org.json shadows the android.jar stub on the plain JVM (PKG-11). */
class OrgJsonSmokeTest {
    @Test
    fun parsesJson() {
        assertEquals(1, JSONObject("""{"a":1}""").getInt("a"))
    }
}
