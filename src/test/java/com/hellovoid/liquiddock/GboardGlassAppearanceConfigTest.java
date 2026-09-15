package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.ConfigSchema;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/** Runtime contract for Gboard-only glass enablement and appearance overrides. */
public class GboardGlassAppearanceConfigTest {
    @Test public void gboardPreferenceKeysAndDefaultsAreStable() {
        assertEquals("liquid_gboard_floating_glass", GboardGlassPreferences.ENABLED_KEY);
        assertEquals("liquid_gboard_blur", GboardGlassPreferences.BLUR_KEY);
        assertEquals("liquid_gboard_tint_r", GboardGlassPreferences.TINT_RED_KEY);
        assertEquals("liquid_gboard_tint_g", GboardGlassPreferences.TINT_GREEN_KEY);
        assertEquals("liquid_gboard_tint_b", GboardGlassPreferences.TINT_BLUE_KEY);
        assertEquals("liquid_gboard_tint_alpha", GboardGlassPreferences.TINT_ALPHA_KEY);
        assertTrue(GboardGlassPreferences.ENABLED_DEFAULT);
    }

    @Test public void missingGboardAppearanceValuesInheritGlobalGlass() {
        Map<String, Object> values = globalValues();
        LiquidDockConfig.Glass base = LiquidDockConfig.from(new ConfigReader(values)).glass;
        GboardGlassPreferences.Appearance appearance =
                GboardGlassPreferences.resolve(new ConfigReader(values), base);

        assertTrue(appearance.enabled);
        assertFalse(appearance.hasAppearanceOverride);
        assertEquals(37f, appearance.blur, 0.001f);
        assertEquals(11, appearance.tintR);
        assertEquals(22, appearance.tintG);
        assertEquals(33, appearance.tintB);
        assertEquals(44, appearance.tintAlpha);
    }

    @Test public void explicitGboardAppearanceValuesOverrideOnlyGboardMaterial() {
        Map<String, Object> values = globalValues();
        values.put(GboardGlassPreferences.BLUR_KEY, 73);
        values.put(GboardGlassPreferences.TINT_RED_KEY, 101);
        values.put(GboardGlassPreferences.TINT_GREEN_KEY, 102);
        values.put(GboardGlassPreferences.TINT_BLUE_KEY, 103);
        values.put(GboardGlassPreferences.TINT_ALPHA_KEY, 104);

        LiquidDockConfig.Glass base = LiquidDockConfig.from(new ConfigReader(values)).glass;
        GboardGlassPreferences.Appearance appearance =
                GboardGlassPreferences.resolve(new ConfigReader(values), base);

        assertEquals(37f, base.blur, 0.001f);
        assertEquals(11, base.tintR);
        assertEquals(22, base.tintG);
        assertEquals(33, base.tintB);
        assertEquals(44, base.tintAlpha);
        assertTrue(appearance.hasAppearanceOverride);
        assertEquals(73f, appearance.blur, 0.001f);
        assertEquals(101, appearance.tintR);
        assertEquals(102, appearance.tintG);
        assertEquals(103, appearance.tintB);
        assertEquals(104, appearance.tintAlpha);
    }

    @Test public void disabledGboardPreferenceDisablesFeatureWithoutDiscardingOverrides() {
        Map<String, Object> values = globalValues();
        values.put(GboardGlassPreferences.ENABLED_KEY, false);
        values.put(GboardGlassPreferences.BLUR_KEY, 73);
        LiquidDockConfig.Glass base = LiquidDockConfig.from(new ConfigReader(values)).glass;
        GboardGlassPreferences.Appearance appearance =
                GboardGlassPreferences.resolve(new ConfigReader(values), base);

        assertFalse(appearance.enabled);
        assertTrue(appearance.hasAppearanceOverride);
        assertEquals(73f, appearance.blur, 0.001f);
    }

    private static Map<String, Object> globalValues() {
        Map<String, Object> values = new HashMap<>();
        values.put(ConfigSchema.Glass.BLUR.name(), 37);
        values.put(ConfigSchema.Glass.TINT_RED.name(), 11);
        values.put(ConfigSchema.Glass.TINT_GREEN.name(), 22);
        values.put(ConfigSchema.Glass.TINT_BLUE.name(), 33);
        values.put(ConfigSchema.Glass.TINT_ALPHA.name(), 44);
        return values;
    }
}
