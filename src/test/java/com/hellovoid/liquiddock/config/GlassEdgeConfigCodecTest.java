package com.hellovoid.liquiddock.config;

import com.hellovoid.liquiddock.GlassEdgeEffects;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class GlassEdgeConfigCodecTest {
    @Test public void exportImportPreservesOptionalBacklightAndWholeShadowIndependently() {
        Map<String,Object> prefs = new HashMap<>();
        String width = GlassEdgeEffects.key(
                GlassEdgeEffects.DOCK, ConfigSchema.Glass.BACKLIT_WIDTH);
        String light = GlassEdgeEffects.key(
                GlassEdgeEffects.DOCK, ConfigSchema.Glass.BACKLIT_ENABLED);
        String shadow = GlassEdgeEffects.key(
                GlassEdgeEffects.FOLDER, ConfigSchema.Glass.OUTER_SHADOW_ENABLED);
        String extent = GlassEdgeEffects.key(
                GlassEdgeEffects.DOCK, ConfigSchema.Glass.BACKLIT_EXTENT);
        prefs.put(extent, 72);
        prefs.put(width, 1);
        prefs.put(width + "_tenths", 14);
        prefs.put(light, true);
        prefs.put(shadow, false);
        Map<String,Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(1.4d, ((Number) exported.get(width)).doubleValue(), .0001d);
        assertEquals(true, exported.get(light));
        assertEquals(false, exported.get(shadow));
        assertEquals(72, exported.get(extent));
        Map<String,Object> imported = ConfigCodec.importValues(exported);
        assertEquals(14, imported.get(width + "_tenths"));
        assertEquals(true, imported.get(light));
        assertEquals(false, imported.get(shadow));
        assertEquals(72, imported.get(extent));
    }

    @Test public void importedExtentIsClampedToValidReach() {
        String reach = GlassEdgeEffects.key(
                GlassEdgeEffects.WIDGET, ConfigSchema.Glass.BACKLIT_EXTENT);
        assertEquals(100, ConfigCodec.importValues(Map.of(reach, 999)).get(reach));
        assertEquals(0, ConfigCodec.importValues(Map.of(reach, -5)).get(reach));
    }

    @Test public void softnessCanReachTwoHundredPercent() {
        String softness = GlassEdgeEffects.key(
                GlassEdgeEffects.DOCK, ConfigSchema.Glass.BACKLIT_SOFTNESS);
        assertEquals(200, ConfigCodec.importValues(Map.of(softness, 300)).get(softness));
        assertEquals(180, ConfigCodec.importValues(Map.of(softness, 180)).get(softness));
    }

    @Test public void importedMaterialValuesAreClamped() {
        String width = GlassEdgeEffects.key(
                GlassEdgeEffects.WIDGET, ConfigSchema.Glass.BACKLIT_WIDTH);
        Map<String,Object> imported = ConfigCodec.importValues(Map.of(width, 999));
        assertEquals(50, imported.get(width + "_tenths"));
    }
}
