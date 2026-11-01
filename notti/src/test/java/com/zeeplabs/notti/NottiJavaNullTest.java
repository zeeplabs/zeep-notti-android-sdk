package com.zeeplabs.notti;

import static org.junit.Assert.assertNull;

import android.app.Application;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Java callers passing null to text, map and list parameters never get an exception (PRR-05, AD-018).
 * Before the fix each of these calls threw a NullPointerException from Kotlin's parameter checks.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class NottiJavaNullTest {
    @Test
    public void nullArgumentsFromJavaDoNotThrow() {
        Application app = RuntimeEnvironment.getApplication();
        NottiLogLevel previous = Notti.getLogLevel();
        Notti.setLogLevel(NottiLogLevel.NONE);
        try {
            Notti.initialize(app, null, "key-fixture");
            Notti.initialize(app, "app-fixture", null);
            Notti.initialize(app, "app-fixture", "key-fixture", null);

            Notti.login(null);
            Notti.trackEvent(null);
            Notti.User.addTag(null, "fixture-value");
            Notti.User.addTag("plan", null);
            Notti.User.addTags(null);
            Notti.User.removeTag(null);
            Notti.User.removeTags(null);
            Notti.User.setEmail(null);
            Notti.User.setPhone(null);

            Map<String, String> tags = new HashMap<>();
            tags.put("plan", null);
            tags.put(null, "fixture-value");
            Notti.User.addTags(tags);
            List<String> keys = new ArrayList<>();
            keys.add(null);
            Notti.User.removeTags(keys);

            assertNull(Notti.getDeviceId());
        } finally {
            Notti.setLogLevel(previous);
        }
    }
}
