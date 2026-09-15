package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.ConfigKey;
import com.hellovoid.liquiddock.config.ConfigSchema;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/** Runtime contract for Gboard-only glass enablement and appearance overrides. */
public class GboardGlassAppearanceConfigTest {
    @Test public void gboardSchemaIsEnabledByDefaultAndOverridesAreOptional() {
        ConfigKey<Boolean> enabled = ConfigSchema.Glass.GBOARD_FLOATING_GLASS;
        assertEquals("liquid_gboard_floating_glass", enabled.name());
        assertEquals(Boolean.TRUE, enabled.uiDefault());
        assertEquals(Boolean.TRUE, enabled.runtimeFallback());
        assertEquals(ConfigKey.ExportMode.ALWAYS, enabled.exportMode());

        assertEquals("liquid_gboard_blur", ConfigSchema.Glass.GBOARD_BLUR.name());
        assertEquals(ConfigKey.ExportMode.IF_PRESENT, ConfigSchema.Glass.GBOARD_BLUR.exportMode());
        assertEquals("liquid_gboard_tint_r", ConfigSchema.Glass.GBOARD_TINT_RED.name());
        assertEquals("liquid_gboard_tint_g", ConfigSchema.Glass.GBOARD_TINT_GREEN.name());
        assertEquals("liquid_gboard_tint_b", ConfigSchema.Glass.GBOARD_TINT_BLUE.name());
        assertEquals("liquid_gboard_tint_alpha", ConfigSchema.Glass.GBOARD_TINT_ALPHA.name());
    }

    @Test public void missingGboardAppearanceValuesInheritGlobalGlass() {
        Map<String, Object> values = new HashMap<>();
        values.put(ConfigSchema.Glass.BLUR.name(), 37);
        values.put(ConfigSchema.Glass.TINT_RED.name(), 11);
        values.put(ConfigSchema.Glass.TINT_GREEN.name(), 22);
        values.put(ConfigSchema.Glass.TINT_BLUE.name(), 33);
        values.put(ConfigSchema.Glass.TINT_ALPHA.name(), 44);

        LiquidDockConfig.Glass glass = LiquidDockConfig.from(new ConfigReader(values)).glass;

        assertTrue(glass.gboardEnabled);
        assertEquals(37f, glass.gboardBlur, 0.001f);
        assertEquals(11, glass.gboardTintR);
        assertEquals(22, glass.gboardTintG);
        assertEquals(33, glass.gboardTintB);
        assertEquals(44, glass.gboardTintAlpha);
    }

    @Test public void explicitGboardAppearanceValuesOverrideOnlyGboardMaterial() {
        Map<String, Object> values = new HashMap<>();
        values.put(ConfigSchema.Glass.BLUR.name(), 37);
        values.put(ConfigSchema.Glass.TINT_RED.name(), 11);
        values.put(ConfigSchema.Glass.TINT_GREEN.name(), 22);
        values.put(ConfigSchema.Glass.TINT_BLUE.name(), 33);
        values.put(ConfigSchema.Glass.TINT_ALPHA.name(), 44);
        values.put(ConfigSchema.Glass.GBOARD_BLUR.name(), 73);
        values.put(ConfigSchema.Glass.GBOARD_TINT_RED.name(), 101);
        values.put(ConfigSchema.Glass.GBOARD_TINT_GREEN.name(), 102);
        values.put(ConfigSchema.Glass.GBOARD_TINT_BLUE.name(), 103);
        values.put(ConfigSchema.Glass.GBOARD_TINT_ALPHA.name(), 104);

        LiquidDockConfig.Glass glass = LiquidDockConfig.from(new ConfigReader(values)).glass;

        assertEquals(37f, glass.blur, 0.001f);
        assertEquals(11, glass.tintR);
        assertEquals(22, glass.tintG);
        assertEquals(33, glass.tintB);
        assertEquals(44, glass.tintAlpha);
        assertEquals(73f, glass.gboardBlur, 0.001f);
        assertEquals(101, glass.gboardTintR);
        assertEquals(102, glass.gboardTintG);
        assertEquals(103, glass.gboardTintB);
        assertEquals(104, glass.gboardTintAlpha);
    }
}
