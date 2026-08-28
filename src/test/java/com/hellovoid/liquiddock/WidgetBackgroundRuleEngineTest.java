package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Test;

public class WidgetBackgroundRuleEngineTest {
    private static WidgetBackgroundRuleEngine parse(String xml) throws Exception {
        return WidgetBackgroundRuleEngine.parse(new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test public void exactProductRuleOutranksPackageAndSpanFallback() throws Exception {
        WidgetBackgroundRuleEngine engine = parse("""
                <widget-background-rules version="1">
                  <rule id="fallback" type="maml" appPackage="com.miui.weather2"
                        configSpanX="4" configSpanY="2">
                    <hide-element name="fallbackBackground"/>
                  </rule>
                  <rule id="exact" type="maml" productId="product-1">
                    <hide-element name="exactBackground"/>
                  </rule>
                </widget-background-rules>
                """);
        WidgetBackgroundRule rule = engine.match(new WidgetBackgroundIdentity(
                "maml", "product-1", "com.miui.weather2", 2, 1, 4, 2));

        assertNotNull(rule);
        assertEquals("exact", rule.id());
        assertEquals(List.of("exactBackground"), rule.elementNames());
    }

    @Test public void allSpecifiedIdentityFieldsMustMatch() throws Exception {
        WidgetBackgroundRuleEngine engine = parse("""
                <widget-background-rules version="1">
                  <rule id="size" type="maml" appPackage="pkg"
                        spanX="2" spanY="1" configSpanX="4" configSpanY="2">
                    <hide-element name="background"/>
                  </rule>
                </widget-background-rules>
                """);

        assertNotNull(engine.match(new WidgetBackgroundIdentity(
                "maml", null, "pkg", 2, 1, 4, 2)));
        assertNull(engine.match(new WidgetBackgroundIdentity(
                "maml", null, "pkg", 2, 2, 4, 2)));
        assertNull(engine.match(new WidgetBackgroundIdentity(
                "remoteviews", null, "pkg", 2, 1, 4, 2)));
    }

    @Test public void oneRuleCanHideMultipleElementsInDocumentOrder() throws Exception {
        WidgetBackgroundRuleEngine engine = parse("""
                <widget-background-rules version="1">
                  <rule id="multi" type="maml" productId="p">
                    <hide-element name="background"/>
                    <hide-element name="glow"/>
                    <hide-element name="plate"/>
                  </rule>
                </widget-background-rules>
                """);
        WidgetBackgroundRule rule = engine.match(new WidgetBackgroundIdentity(
                "maml", "p", null, -1, -1, -1, -1));

        assertNotNull(rule);
        assertEquals(List.of("background", "glow", "plate"), rule.elementNames());
    }

    @Test public void diagnosticOnlyRuleHasNoDestructiveActions() throws Exception {
        WidgetBackgroundRuleEngine engine = parse("""
                <widget-background-rules version="1">
                  <rule id="diagnostic" type="maml" appPackage="pkg"/>
                </widget-background-rules>
                """);
        WidgetBackgroundRule rule = engine.match(new WidgetBackgroundIdentity(
                "maml", "unknown", "pkg", 1, 1, 2, 2));

        assertNotNull(rule);
        assertTrue(rule.elementNames().isEmpty());
    }

    @Test public void unknownIdentityMatchesNothing() throws Exception {
        WidgetBackgroundRuleEngine engine = parse("""
                <widget-background-rules version="1">
                  <rule id="known" type="maml" productId="known-product">
                    <hide-element name="background"/>
                  </rule>
                </widget-background-rules>
                """);

        assertNull(engine.match(new WidgetBackgroundIdentity(
                "maml", "other", "other.pkg", 1, 1, 2, 2)));
    }

    @Test public void malformedXmlFailsClosed() throws Exception {
        WidgetBackgroundRuleEngine engine = parse("<widget-background-rules><rule");
        assertNull(engine.match(new WidgetBackgroundIdentity(
                "maml", "anything", "pkg", 1, 1, 2, 2)));
    }
}
