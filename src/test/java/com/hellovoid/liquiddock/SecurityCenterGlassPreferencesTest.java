package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.ConfigSchema;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;

public class SecurityCenterGlassPreferencesTest {
    private static Map<String, Object> enabled() {
        Map<String, Object> v = new HashMap<>();
        v.put(ConfigSchema.Core.ENABLED.name(), true);
        v.put(ConfigSchema.Glass.ENABLED.name(), true);
        v.put(ConfigSchema.Glass.SECURITY_CENTER_GLASS.name(), true);
        return v;
    }

    private static ThirdPartyGlassAppearance appearance(Map<String, Object> values) {
        ConfigReader reader = new ConfigReader(values);
        return SecurityCenterGlassPreferences.resolve(reader, LiquidDockConfig.from(reader).glass);
    }

    @Test public void unconfiguredSidebarInheritsGlobalWithoutFreezingValues() {
        Map<String, Object> v = enabled();
        v.put(ConfigSchema.Glass.BLUR.name(), 18);
        v.put(ConfigSchema.Glass.TINT_RED.name(), 40);
        v.put(ConfigSchema.Glass.TINT_ALPHA.name(), 64);
        ThirdPartyGlassAppearance first = appearance(v);
        assertTrue(first.enabled);
        assertFalse(first.hasAppearanceOverride);
        assertEquals(18f, first.blur, 0.0001f);
        assertEquals(40, first.tintR);
        assertEquals(64, first.tintAlpha);
        assertEquals(-1f, first.cornerRadiusOverrideDp, 0.0001f);

        v.put(ConfigSchema.Glass.BLUR.name(), 26);
        assertEquals(26f, appearance(v).blur, 0.0001f);
    }

    @Test public void sidebarOverridesAreIndependentAndBounded() {
        Map<String, Object> v = enabled();
        v.put("third_party_glass.sidebar.blur", 20f);
        v.put("third_party_glass.sidebar.tint_r", 255);
        v.put("third_party_glass.sidebar.tint_g", 110);
        v.put("third_party_glass.sidebar.tint_b", 40);
        v.put("third_party_glass.sidebar.corner_radius_dp", 24f);
        v.put("third_party_glass.sidebar.capture_scale_percent", 70);
        v.put("third_party_glass.sidebar.render_fps", 24);
        ThirdPartyGlassAppearance a = appearance(v);
        assertTrue(a.hasAppearanceOverride);
        assertEquals(20f, a.blur, 0.0001f);
        assertEquals(110, a.tintG);
        assertEquals(24f, a.cornerRadiusOverrideDp, 0.0001f);
        assertEquals(70, a.captureScalePercent);
        assertEquals(24, a.renderFps);
    }

    @Test public void globalOrSecuritySwitchCannotBeBypassedByProfile() {
        Map<String, Object> v = enabled();
        v.put("third_party_glass.sidebar.enabled", true);
        v.put(ConfigSchema.Glass.SECURITY_CENTER_GLASS.name(), false);
        assertFalse(appearance(v).enabled);
        v.put(ConfigSchema.Glass.SECURITY_CENTER_GLASS.name(), true);
        v.put(ConfigSchema.Core.ENABLED.name(), false);
        assertFalse(appearance(v).enabled);
    }

    @Test public void highlightOnlyOverridesNamedFamilyAndRetainsOtherLights() {
        Map<String, Object> v = enabled();
        v.put("third_party_glass.sidebar.highlight_sky_haze", false);
        ThirdPartyGlassAppearance a = appearance(v);
        assertFalse(a.highlightProfile.skyHaze);
        LiquidDockConfig.Glass global = LiquidDockConfig.from(new ConfigReader(v)).glass;
        assertEquals(global.largeSurfaceHighlightProfile.specular, a.highlightProfile.specular);
        assertEquals(global.largeSurfaceHighlightProfile.caustics, a.highlightProfile.caustics);
    }
}
