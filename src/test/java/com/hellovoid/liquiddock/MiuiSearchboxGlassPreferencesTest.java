package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class MiuiSearchboxGlassPreferencesTest {
    @Test
    public void missingSearchboxAppearanceInheritsGlobalGlass() {
        Map<String, Object> values = new HashMap<>();
        values.put("liquid_blur", 19f);
        values.put("liquid_tint_r", 9);
        values.put("liquid_tint_g", 18);
        values.put("liquid_tint_b", 27);
        values.put("liquid_tint_alpha", 36);
        ConfigReader reader = new ConfigReader(values);
        LiquidDockConfig.Glass base = LiquidDockConfig.from(reader).glass;

        ThirdPartyGlassAppearance appearance = MiuiSearchboxGlassPreferences.resolve(reader, base);

        assertTrue(appearance.enabled);
        assertFalse(appearance.hasAppearanceOverride);
        assertEquals(base.blur, appearance.blur, 0.001f);
        assertEquals(base.tintR, appearance.tintR);
        assertEquals(base.tintG, appearance.tintG);
        assertEquals(base.tintB, appearance.tintB);
        assertEquals(base.tintAlpha, appearance.tintAlpha);
    }

    @Test
    public void publicSearchboxKeysOverrideSharedProfileAppearance() {
        Map<String, Object> values = new HashMap<>();
        values.put("third_party_glass.miui.searchbox.blur", 8f);
        values.put("third_party_glass.miui.searchbox.tint_r", 10);
        values.put(MiuiSearchboxGlassPreferences.BLUR_KEY, 42f);
        values.put(MiuiSearchboxGlassPreferences.TINT_RED_KEY, 100);
        values.put(MiuiSearchboxGlassPreferences.TINT_GREEN_KEY, 110);
        values.put(MiuiSearchboxGlassPreferences.TINT_BLUE_KEY, 120);
        values.put(MiuiSearchboxGlassPreferences.TINT_ALPHA_KEY, 130);
        ConfigReader reader = new ConfigReader(values);
        LiquidDockConfig.Glass base = LiquidDockConfig.from(new ConfigReader(new HashMap<>())).glass;

        ThirdPartyGlassAppearance appearance = MiuiSearchboxGlassPreferences.resolve(reader, base);

        assertTrue(appearance.hasAppearanceOverride);
        assertEquals(42f, appearance.blur, 0.001f);
        assertEquals(100, appearance.tintR);
        assertEquals(110, appearance.tintG);
        assertEquals(120, appearance.tintB);
        assertEquals(130, appearance.tintAlpha);
    }

    @Test
    public void hiddenProfileQualityControlsRemainAvailableWithoutGuiKeys() {
        Map<String, Object> values = new HashMap<>();
        values.put("third_party_glass.miui.searchbox.capture_scale_percent", 72);
        values.put("third_party_glass.miui.searchbox.render_fps", 39);
        values.put("third_party_glass.miui.searchbox.corner_radius_dp", 24f);
        values.put("third_party_glass.miui.searchbox.fresh_on_resume", false);
        ConfigReader reader = new ConfigReader(values);
        LiquidDockConfig.Glass base = LiquidDockConfig.from(new ConfigReader(new HashMap<>())).glass;

        ThirdPartyGlassAppearance appearance = MiuiSearchboxGlassPreferences.resolve(reader, base);

        assertEquals(72, appearance.captureScalePercent);
        assertEquals(39, appearance.renderFps);
        assertEquals(24f, appearance.cornerRadiusOverrideDp, 0.001f);
        assertFalse(appearance.freshOnResume);
    }
}
