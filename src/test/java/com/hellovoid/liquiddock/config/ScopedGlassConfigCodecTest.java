package com.hellovoid.liquiddock.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.ScopedGlassOptics;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class ScopedGlassConfigCodecTest {
    @Test public void partialScopeBackupRetainsIndependentDecimalAndBooleanOverrides() {
        String lens = ScopedGlassOptics.key(
                ScopedGlassOptics.GBOARD, ConfigSchema.Glass.LENS_REFRACTION);
        String normals = ScopedGlassOptics.normalsKey(ScopedGlassOptics.SEARCHBOX);
        LinkedHashMap<String, Object> prefs = new LinkedHashMap<>();
        prefs.put(lens, 3);
        prefs.put(lens + "_tenths", 27);
        prefs.put(normals, true);

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(2.7d, ((Number) exported.get(lens)).doubleValue(), 0.001d);
        assertEquals(true, exported.get(normals));
        Map<String, Object> imported = ConfigCodec.importValues(exported);
        assertEquals(27, imported.get(lens + "_tenths"));
        assertEquals(true, imported.get(normals));
        assertFalse(imported.containsKey(
                ScopedGlassOptics.key(ScopedGlassOptics.DIALOG,
                        ConfigSchema.Glass.LENS_REFRACTION)));
    }

    @Test public void popupRoundTripPreservesFractionalBlurTintAndHighlight() {
        String blur = ScopedGlassOptics.key(ScopedGlassOptics.POPUP, ConfigSchema.Glass.BLUR);
        String tint = ScopedGlassOptics.key(
                ScopedGlassOptics.POPUP, ConfigSchema.Glass.TINT_ALPHA);
        String caustics = ScopedGlassOptics.popupHighlightKey(
                ConfigSchema.LauncherHighlight.LARGE_CAUSTICS);
        LinkedHashMap<String, Object> prefs = new LinkedHashMap<>();
        prefs.put(blur, 14);
        prefs.put(blur + "_tenths", 145);
        prefs.put(tint, 80);
        prefs.put(caustics, false);
        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(14.5d, ((Number) exported.get(blur)).doubleValue(), 0.0001d);
        assertEquals(80, exported.get(tint));
        assertEquals(false, exported.get(caustics));
        Map<String, Object> imported = ConfigCodec.importValues(exported);
        assertEquals(145, imported.get(blur + "_tenths"));
        assertEquals(80, imported.get(tint));
        assertEquals(false, imported.get(caustics));
        // The standard backup intentionally includes current global defaults too.
        assertEquals(ConfigSchema.Glass.BLUR.exportDefault(),
                imported.get(ConfigSchema.Glass.BLUR.name()));
    }

    @Test public void invalidValuesAreClampedWithoutCrossScopeWrites() {
        String ior = ScopedGlassOptics.key(
                ScopedGlassOptics.DIALOG, ConfigSchema.Glass.IOR);
        Map<String, Object> imported = ConfigCodec.importValues(Map.of(ior, 99999));
        assertEquals(ConfigSchema.Glass.IOR.maxInt(), imported.get(ior));
        assertFalse(imported.containsKey(
                ScopedGlassOptics.key(ScopedGlassOptics.GBOARD, ConfigSchema.Glass.IOR)));
    }
}
