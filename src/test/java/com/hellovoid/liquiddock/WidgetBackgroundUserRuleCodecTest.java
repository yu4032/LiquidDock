package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class WidgetBackgroundUserRuleCodecTest {
    @Test public void roundTripPreservesExactIdentityTargetAndSpecialCharacters() {
        WidgetBackgroundUserRule rule = new WidgetBackgroundUserRule(
                new WidgetBackgroundIdentity("maml", "product|1", "pkg.weather", 2, 1, 4, 2),
                WidgetBackgroundUserRule.TargetKind.MAML_ELEMENT,
                "sky Color/背景");

        String encoded = WidgetBackgroundUserRuleCodec.encode(List.of(rule));
        List<WidgetBackgroundUserRule> decoded = WidgetBackgroundUserRuleCodec.decode(encoded);

        assertEquals(1, decoded.size());
        WidgetBackgroundUserRule restored = decoded.get(0);
        assertEquals(WidgetBackgroundUserRule.TargetKind.MAML_ELEMENT, restored.targetKind());
        assertEquals("sky Color/背景", restored.target());
        assertTrue(restored.matches(new WidgetBackgroundIdentity(
                "maml", "product|1", "pkg.weather", 2, 1, 4, 2)));
        assertFalse(restored.matches(new WidgetBackgroundIdentity(
                "maml", "other", "pkg.weather", 2, 1, 4, 2)));
    }

    @Test public void remoteViewsResourceRuleUsesSameExactIdentityContract() {
        WidgetBackgroundUserRule rule = new WidgetBackgroundUserRule(
                new WidgetBackgroundIdentity("remoteviews", null, "com.example.clock", 2, 2, 2, 2),
                WidgetBackgroundUserRule.TargetKind.REMOTE_VIEWS_RESOURCE,
                "com.example.clock:id/card_background");

        assertTrue(rule.matches(new WidgetBackgroundIdentity(
                "remoteviews", null, "com.example.clock", 2, 2, 2, 2)));
        assertFalse(rule.matches(new WidgetBackgroundIdentity(
                "remoteviews", null, "com.example.clock", 4, 2, 4, 2)));
    }

    @Test public void malformedRowsFailClosedWithoutDroppingValidRows() {
        WidgetBackgroundUserRule valid = new WidgetBackgroundUserRule(
                new WidgetBackgroundIdentity("maml", "p", "pkg", 1, 1, 1, 1),
                WidgetBackgroundUserRule.TargetKind.MAML_ELEMENT,
                "background");
        String mixed = WidgetBackgroundUserRuleCodec.encode(List.of(valid)) + "\nnot-a-valid-rule";

        List<WidgetBackgroundUserRule> decoded = WidgetBackgroundUserRuleCodec.decode(mixed);
        assertEquals(1, decoded.size());
        assertEquals("background", decoded.get(0).target());
    }
}
