package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.prismal.PrismalEdgeDecoration;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class GlassEdgeEffectsTest {
    @Test public void defaultIsOptInAndDoesNotChangeExistingGlass() {
        PrismalEdgeDecoration defaultStyle = GlassEdgeEffects.resolve(
                new ConfigReader(new HashMap<>()), GlassEdgeEffects.GLOBAL);
        assertFalse(defaultStyle.hasShadow());
        assertFalse(defaultStyle.hasStroke());
    }

    @Test public void followsGlobalUntilExplicitComponentOverride() {
        Map<String,Object> prefs = new HashMap<>();
        prefs.put(ConfigSchema.Glass.OUTER_SHADOW_ENABLED.name(), true);
        prefs.put(ConfigSchema.Glass.OUTER_SHADOW_ALPHA.name(), 51);
        prefs.put(ConfigSchema.Glass.OUTER_STROKE_ENABLED.name(), true);
        String widgetSwitch = GlassEdgeEffects.key(
                GlassEdgeEffects.WIDGET, ConfigSchema.Glass.OUTER_SHADOW_ENABLED);
        String popupAlpha = GlassEdgeEffects.key(
                GlassEdgeEffects.POPUP, ConfigSchema.Glass.OUTER_SHADOW_ALPHA);
        prefs.put(widgetSwitch, false);
        prefs.put(popupAlpha, 120);
        ConfigReader reader = new ConfigReader(prefs);
        PrismalEdgeDecoration global = GlassEdgeEffects.resolve(reader, GlassEdgeEffects.GLOBAL);
        PrismalEdgeDecoration widget = GlassEdgeEffects.resolve(reader, GlassEdgeEffects.WIDGET);
        PrismalEdgeDecoration popup = GlassEdgeEffects.resolve(reader, GlassEdgeEffects.POPUP);
        assertTrue(global.hasShadow());
        assertTrue(global.hasStroke());
        assertFalse(widget.hasShadow());
        assertTrue(widget.hasStroke());
        assertEquals(51f / 255f, global.shadowA, 0.0001f);
        assertEquals(120f / 255f, popup.shadowA, 0.0001f);
        assertNotEquals(widgetSwitch, popupAlpha);
    }

    @Test public void decimalOverridesShadowRadiusAndNativeOwnerOnlyKeepsStroke() {
        Map<String,Object> prefs = new HashMap<>();
        prefs.put(ConfigSchema.Glass.OUTER_SHADOW_ENABLED.name(), true);
        prefs.put(ConfigSchema.Glass.OUTER_STROKE_ENABLED.name(), true);
        String radius = GlassEdgeEffects.key(
                GlassEdgeEffects.HANDLE_MENU, ConfigSchema.Glass.OUTER_SHADOW_RADIUS);
        prefs.put(radius, 9);
        prefs.put(radius + "_tenths", 94);
        ConfigReader reader = new ConfigReader(prefs);
        PrismalEdgeDecoration style = GlassEdgeEffects.resolve(reader, GlassEdgeEffects.HANDLE_MENU);
        assertEquals(9.4f, style.shadowRadiusDp, 0.0001f);
        GlassEdgeEffects.refresh(reader);
        PrismalEdgeDecoration withNativeShadow = GlassEdgeEffects.strokeOnly(GlassEdgeEffects.HANDLE_MENU);
        assertFalse(withNativeShadow.hasShadow());
        assertTrue(withNativeShadow.hasStroke());
        assertTrue(style.extraGuardPx(2f) > withNativeShadow.extraGuardPx(2f));
    }
}
