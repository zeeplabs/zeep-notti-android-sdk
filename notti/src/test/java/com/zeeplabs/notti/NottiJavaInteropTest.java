package com.zeeplabs.notti;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Every public method called from Java with Java lambdas (API-14). Uses only the public API: the
 * production runtime has no FirebaseApp here, so no token and no network.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class NottiJavaInteropTest {
    @Test
    public void everyPublicMethodIsCallableFromJava() {
        Application app = RuntimeEnvironment.getApplication();
        NottiLogLevel previous = Notti.getLogLevel();
        Notti.setLogLevel(NottiLogLevel.NONE);
        try {
            Notti.initialize(app, "app-fixture", "key-fixture");
            Notti.initialize(app, "app-fixture", "key-fixture", new NottiOptions());
            Notti.initialize(app, "app-fixture", "key-fixture", new NottiOptions("https://push.example.invalid"));
            NottiOptions noUrlOpening = new NottiOptions("https://push.example.invalid", false);
            assertFalse(noUrlOpening.getOpensInAppUrls());
            Notti.initialize(app, "app-fixture", "key-fixture", noUrlOpening);

            Notti.login("user-fixture");
            Notti.logout();
            Notti.setSubscription(true);
            Notti.setLocationSharingEnabled(false);
            String deviceId = Notti.getDeviceId();
            assertNull(deviceId);

            Map<String, String> tags = new HashMap<>();
            tags.put("tier", "fixture-tier");
            Notti.User.addTag("plan", "fixture-plan");
            Notti.User.addTags(tags);
            Notti.User.removeTag("plan");
            Notti.User.removeTags(Collections.singletonList("tier"));
            Notti.User.setEmail("user@example.invalid");
            Notti.User.clearEmail();
            Notti.User.setPhone("+10000000000");
            Notti.User.clearPhone();

            List<NottiNotification> received = new ArrayList<>();
            List<String> deviceIds = new ArrayList<>();
            NottiSubscription receivedSubscription = Notti.Notifications.addReceivedListener(received::add);
            NottiSubscription clickedSubscription = Notti.Notifications.addClickedListener(notification -> { });
            NottiSubscription deviceIdSubscription = Notti.addDeviceIdChangedListener(deviceIds::add);

            Boolean[] answer = {null};
            Notti.Notifications.requestPermission(granted -> answer[0] = granted);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(Boolean.FALSE, answer[0]);

            NottiNotification initial = Notti.Notifications.takeInitialClick();
            assertNull(initial);

            Notti.trackEvent("fixture-event");
            List<String> clickedButtons = new ArrayList<>();
            NottiSubscription inAppSubscription = Notti.InAppMessages.addClickListener(click -> {
                NottiInAppAction action = click.getAction();
                boolean isLink = action != null && action.getType() == NottiInAppAction.Type.URL;
                clickedButtons.add(click.getMessageId() + ":" + click.getButtonId() + ":" + isLink);
            });
            inAppSubscription.remove();
            assertTrue(clickedButtons.isEmpty());

            receivedSubscription.remove();
            clickedSubscription.remove();
            deviceIdSubscription.remove();
            deviceIdSubscription.remove();
            assertEquals(NottiLogLevel.NONE, Notti.getLogLevel());
        } finally {
            Notti.setLogLevel(previous);
        }
    }
}
