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
        prefs.put(width, 7);
        prefs.put(width + "_tenths", 74);
        prefs.put(light, true);
        prefs.put(shadow, false);
        Map<String,Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(7.4d, ((Number) exported.get(width)).doubleValue(), .0001d);
        assertEquals(true, exported.get(light));
        assertEquals(false, exported.get(shadow));
        Map<String,Object> imported = ConfigCodec.importValues(exported);
        assertEquals(74, imported.get(width + "_tenths"));
        assertEquals(true, imported.get(light));
        assertEquals(false, imported.get(shadow));
    }

    @Test public void importedMaterialValuesAreClamped() {
        String width = GlassEdgeEffects.key(
                GlassEdgeEffects.WIDGET, ConfigSchema.Glass.BACKLIT_WIDTH);
        Map<String,Object> imported = ConfigCodec.importValues(Map.of(width, 999));
        assertEquals(240, imported.get(width + "_tenths"));
    }
}
