package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public class WidgetBackgroundDiscoveryCodecTest {
    @Test public void discoveryRoundTripPreservesIdentityAndTargets() {
        WidgetBackgroundIdentity identity = new WidgetBackgroundIdentity(
                "maml", "weather-product", "com.miui.weather2", 4, 2, 4, 2);
        WidgetBackgroundDiscoverySnapshot snapshot = new WidgetBackgroundDiscoverySnapshot(
                identity,
                List.of(
                        new WidgetBackgroundDiscoveryTarget(
                                WidgetBackgroundUserRule.TargetKind.MAML_ELEMENT,
                                "background", "RectangleScreenElement"),
                        new WidgetBackgroundDiscoveryTarget(
                                WidgetBackgroundUserRule.TargetKind.MAML_ELEMENT,
                                "skyColor", "ColorScreenElement")),
                123456789L);

        String encoded = WidgetBackgroundDiscoveryCodec.encode(snapshot);
        WidgetBackgroundDiscoverySnapshot restored = WidgetBackgroundDiscoveryCodec.decode(encoded);

        assertEquals("maml", restored.identity().type);
        assertEquals("weather-product", restored.identity().productId);
        assertEquals(4, restored.identity().spanX);
        assertEquals(2, restored.targets().size());
        assertEquals("background", restored.targets().get(0).name());
        assertEquals("RectangleScreenElement", restored.targets().get(0).detail());
        assertEquals(123456789L, restored.lastSeenMillis());
    }

    @Test public void discoveryPreferenceKeyIsStableForTheExactIdentity() {
        WidgetBackgroundIdentity identity = new WidgetBackgroundIdentity(
                "remoteviews", null, "com.example.clock", 2, 2, 2, 2);
        assertEquals(
                WidgetBackgroundDiscoveryCodec.preferenceKey(identity),
                WidgetBackgroundDiscoveryCodec.preferenceKey(identity));
    }
}
