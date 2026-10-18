package com.hellovoid.liquiddock.config;

import com.hellovoid.liquiddock.GlassEdgeEffects;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class GlassEdgeConfigCodecTest {
    @Test public void roundTripPreservesIndependentShadowStrokeSwitchesAndFractionalRadius() {
        Map<String,Object> prefs = new HashMap<>();
        String radius = GlassEdgeEffects.key(GlassEdgeEffects.WIDGET,
                ConfigSchema.Glass.OUTER_SHADOW_RADIUS);
        String stroke = GlassEdgeEffects.key(GlassEdgeEffects.POPUP,
                ConfigSchema.Glass.OUTER_STROKE_ENABLED);
        String disableShadow = GlassEdgeEffects.key(GlassEdgeEffects.HANDLE_MENU,
                ConfigSchema.Glass.OUTER_SHADOW_ENABLED);
        prefs.put(radius, 13);
        prefs.put(radius + "_tenths", 134);
        prefs.put(stroke, true);
        prefs.put(disableShadow, false);
        Map<String,Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(13.4d, ((Number) exported.get(radius)).doubleValue(), 0.001d);
        assertEquals(true, exported.get(stroke));
        assertEquals(false, exported.get(disableShadow));
        Map<String,Object> imported = ConfigCodec.importValues(exported);
        assertEquals(134, imported.get(radius + "_tenths"));
        assertEquals(true, imported.get(stroke));
        assertEquals(false, imported.get(disableShadow));
        assertFalse(imported.containsKey(GlassEdgeEffects.key(GlassEdgeEffects.FOLDER,
                ConfigSchema.Glass.OUTER_SHADOW_RADIUS)));
    }

    @Test public void clampsUnexpectedValuesAndRejectsNaN() {
        String width = GlassEdgeEffects.key(GlassEdgeEffects.POPUP,
                ConfigSchema.Glass.OUTER_STROKE_WIDTH);
        Map<String,Object> values = ConfigCodec.importValues(Map.of(width, 999));
        assertEquals(40, values.get(width + "_tenths"));
        values = ConfigCodec.importValues(Map.of(width, Double.NaN));
        assertFalse(values.containsKey(width));
    }
}
