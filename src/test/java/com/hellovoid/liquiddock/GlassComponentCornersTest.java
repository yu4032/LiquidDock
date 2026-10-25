package com.hellovoid.liquiddock;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class GlassComponentCornersTest {
    @Test public void automaticPreservesNativeAndZeroMakesSquareCorners() {
        assertEquals(28f, GlassComponentCorners.resolve(-1, 28, 3, 200, 100), .0001f);
        assertEquals(0f, GlassComponentCorners.resolve(0, 28, 3, 200, 100), 0);
        assertEquals(25f, GlassComponentCorners.resolve(12.5f, 28, 2, 200, 100), .0001f);
    }

    @Test public void radiusFitsCurrentSize() {
        assertEquals(20f, GlassComponentCorners.resolve(160, 28, 3, 80, 40), .0001f);
        assertEquals(40f, GlassComponentCorners.resolve(-1, 200, 2, 200, 80), .0001f);
    }

    @Test public void malformedValuesDoNotProduceInvalidGeometry() {
        assertEquals(28f, GlassComponentCorners.resolve(Float.NaN, 28, 2, 200, 100), 0);
        assertEquals(0f, GlassComponentCorners.resolve(-1, Float.NaN, 2, 200, 100), 0);
        assertEquals(12f, GlassComponentCorners.resolve(12, 28, Float.NaN, 200, 100), 0);
        assertEquals(0f, GlassComponentCorners.resolve(12, 28, 2, 0, 100), 0);
    }

    @Test public void oldConfigurationKeepsAllThreeNativeRadii() {
        LiquidDockConfig.Glass glass = new LiquidDockConfig.Glass(new ConfigReader(Map.of()));
        assertEquals(-1f, glass.dialogCornerRadiusDp, 0);
        assertEquals(-1f, glass.shortcutPopupCornerRadiusDp, 0);
        assertEquals(-1f, glass.handleMenuCornerRadiusDp, 0);
    }

    @Test public void independentFractionalSettingsSurviveScopedOpticalOverrides() {
        ConfigReader reader = new ConfigReader(Map.of(
                "liquid_dialog_corner_radius_tenths", 155,
                "liquid_shortcut_popup_corner_radius", 0,
                "liquid_handle_menu_corner_radius", 48,
                "liquid_scope_popup_ior", 150));
        LiquidDockConfig.Glass base = LiquidDockConfig.from(reader).glass;
        LiquidDockConfig.Glass resolved = ScopedGlassOptics.resolve(reader, base, ScopedGlassOptics.POPUP);
        assertEquals(15.5f, resolved.dialogCornerRadiusDp, 0);
        assertEquals(0f, resolved.shortcutPopupCornerRadiusDp, 0);
        assertEquals(48f, resolved.handleMenuCornerRadiusDp, 0);
    }
    @Test public void corruptOrOutOfRangeSettingsAreBounded() {
        LiquidDockConfig.Glass glass = new LiquidDockConfig.Glass(new ConfigReader(Map.of(
                "liquid_dialog_corner_radius", Float.NaN,
                "liquid_shortcut_popup_corner_radius", 999,
                "liquid_handle_menu_corner_radius", -20)));
        assertEquals(-1f, glass.dialogCornerRadiusDp, 0);
        assertEquals(160f, glass.shortcutPopupCornerRadiusDp, 0);
        assertEquals(-1f, glass.handleMenuCornerRadiusDp, 0);
    }

    @Test public void menuRadiusAndDecimalSidecarsReachLiveUpdateOwners() {
        assertTrue(LiveGlassConfigState.isLiveKey("liquid_shortcut_popup_corner_radius"));
        assertTrue(LiveGlassConfigState.isLiveKey("liquid_shortcut_popup_corner_radius_tenths"));
        assertTrue(ExternalGlassLiveConfigState.isRelevantKey("liquid_handle_menu_corner_radius_tenths"));
    }

}
