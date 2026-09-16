package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class ThirdPartyGlassProfilesTest {
    @Test
    public void absentProfileInheritsGlobalAppearanceAndAdapterDefaults() {
        Map<String, Object> values = new HashMap<>();
        values.put("liquid_blur", 31f);
        values.put("liquid_tint_r", 11);
        values.put("liquid_tint_g", 22);
        values.put("liquid_tint_b", 33);
        values.put("liquid_tint_alpha", 44);
        values.put("liquid_passblur_capture_scale", 63);
        values.put("liquid_passblur_render_fps", 47);
        ConfigReader reader = new ConfigReader(values);
        LiquidDockConfig.Glass base = LiquidDockConfig.from(reader).glass;

        ThirdPartyGlassAppearance appearance = ThirdPartyGlassProfiles.resolve(
                reader,
                "example.adapter",
                base,
                new ThirdPartyGlassProfiles.Defaults(false, true, -1f));

        assertFalse(appearance.enabled);
        assertFalse(appearance.hasAppearanceOverride);
        assertEquals(base.blur, appearance.blur, 0.001f);
        assertEquals(base.tintR, appearance.tintR);
        assertEquals(base.tintG, appearance.tintG);
        assertEquals(base.tintB, appearance.tintB);
        assertEquals(base.tintAlpha, appearance.tintAlpha);
        assertEquals(base.passBlurCaptureScalePercent, appearance.captureScalePercent);
        assertEquals(base.passBlurRenderFps, appearance.renderFps);
        assertEquals(-1f, appearance.cornerRadiusOverrideDp, 0.001f);
        assertTrue(appearance.freshOnResume);
    }

    @Test
    public void explicitProfileOverridesAreClampedAndMarked() {
        Map<String, Object> values = new HashMap<>();
        values.put("third_party_glass.example.adapter.enabled", true);
        values.put("third_party_glass.example.adapter.blur", 88f);
        values.put("third_party_glass.example.adapter.tint_r", 300);
        values.put("third_party_glass.example.adapter.tint_g", -2);
        values.put("third_party_glass.example.adapter.tint_b", 77);
        values.put("third_party_glass.example.adapter.tint_alpha", 190);
        values.put("third_party_glass.example.adapter.capture_scale_percent", 500);
        values.put("third_party_glass.example.adapter.render_fps", -1);
        values.put("third_party_glass.example.adapter.corner_radius_dp", 26f);
        values.put("third_party_glass.example.adapter.fresh_on_resume", false);
        ConfigReader reader = new ConfigReader(values);
        LiquidDockConfig.Glass base = LiquidDockConfig.from(new ConfigReader(new HashMap<>())).glass;

        ThirdPartyGlassAppearance appearance = ThirdPartyGlassProfiles.resolve(
                reader,
                "example.adapter",
                base,
                new ThirdPartyGlassProfiles.Defaults(false, true, -1f));

        assertTrue(appearance.enabled);
        assertTrue(appearance.hasAppearanceOverride);
        assertEquals(88f, appearance.blur, 0.001f);
        assertEquals(255, appearance.tintR);
        assertEquals(0, appearance.tintG);
        assertEquals(77, appearance.tintB);
        assertEquals(190, appearance.tintAlpha);
        assertEquals(PassBlurQualityPolicy.MAX_CAPTURE_SCALE_PERCENT, appearance.captureScalePercent);
        assertEquals(0, appearance.renderFps);
        assertEquals(26f, appearance.cornerRadiusOverrideDp, 0.001f);
        assertFalse(appearance.freshOnResume);
    }

    @Test
    public void invalidProfileIdsAreRejectedBeforeKeyConstruction() {
        LiquidDockConfig.Glass base = LiquidDockConfig.from(new ConfigReader(new HashMap<>())).glass;
        assertThrows(IllegalArgumentException.class, () -> ThirdPartyGlassProfiles.resolve(
                new ConfigReader(new HashMap<>()),
                "bad/profile",
                base,
                new ThirdPartyGlassProfiles.Defaults(false, true, -1f)));
    }
}
