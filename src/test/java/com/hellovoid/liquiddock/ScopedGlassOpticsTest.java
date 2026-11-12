package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.ConfigSchema;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** Independent profiles inherit global optics until that scope is explicitly overridden. */
public class ScopedGlassOpticsTest {
    @Test public void threeScopesHaveDisjointKeysAndSameOpticalCatalog() {
        String g = ScopedGlassOptics.key(ScopedGlassOptics.GBOARD, ConfigSchema.Glass.LENS_REFRACTION);
        String s = ScopedGlassOptics.key(ScopedGlassOptics.SEARCHBOX, ConfigSchema.Glass.LENS_REFRACTION);
        String d = ScopedGlassOptics.key(ScopedGlassOptics.DIALOG, ConfigSchema.Glass.LENS_REFRACTION);
        assertFalse(g.equals(s));
        assertFalse(s.equals(d));
        assertFalse(g.equals(d));
        assertTrue(g.startsWith("liquid_"));
        // Four legacy per-scope highlight strength controls were replaced by OS4.
        assertEquals(41, ScopedGlassOptics.opticalKeys().size());
    }

    @Test public void popupOverridesBasicOpticsAndHighlightsWithoutChangingGlobalMaterial() {
        Map<String, Object> values = new HashMap<>();
        values.put(ConfigSchema.Glass.BLUR.name(), 7);
        values.put(ConfigSchema.Glass.BLUR.name() + "_tenths", 73);
        values.put(ConfigSchema.Glass.TINT_ALPHA.name(), 35);
        values.put(ConfigSchema.Glass.IOR.name(), 150);
        values.put(ConfigSchema.LauncherHighlight.LARGE_CAUSTICS.name(), true);
        values.put(ScopedGlassOptics.key(ScopedGlassOptics.POPUP,
                ConfigSchema.Glass.BLUR), 14);
        values.put(ScopedGlassOptics.key(ScopedGlassOptics.POPUP,
                ConfigSchema.Glass.BLUR) + "_tenths", 145);
        values.put(ScopedGlassOptics.key(ScopedGlassOptics.POPUP,
                ConfigSchema.Glass.TINT_ALPHA), 90);
        values.put(ScopedGlassOptics.key(ScopedGlassOptics.POPUP,
                ConfigSchema.Glass.IOR), 181);
        values.put(ScopedGlassOptics.popupHighlightKey(
                ConfigSchema.LauncherHighlight.LARGE_CAUSTICS), false);

        ConfigReader reader = new ConfigReader(values);
        LiquidDockConfig.Glass global = LiquidDockConfig.from(reader).glass;
        LiquidDockConfig.Glass popup = ScopedGlassOptics.resolve(
                reader, global, ScopedGlassOptics.POPUP);
        LiquidDockConfig.Glass dialog = ScopedGlassOptics.resolve(
                reader, global, ScopedGlassOptics.DIALOG);
        assertEquals(7.3f, global.blur, 0.0001f);
        assertEquals(14.5f, popup.blur, 0.0001f);
        assertEquals(90, popup.tintAlpha);
        assertEquals(1.81f, popup.ior, 0.0001f);
        assertFalse(popup.largeSurfaceHighlightProfile.caustics);
        assertTrue(global.largeSurfaceHighlightProfile.caustics);
        assertEquals(7.3f, dialog.blur, 0.0001f);
        assertEquals(35, global.tintAlpha);
    }

    @Test public void handleMenuInheritsGlobalUntilItsOwnOverrideAndNeverChangesPopup() {
        Map<String, Object> values = new HashMap<>();
        values.put(ConfigSchema.Glass.BLUR.name(), 9);
        values.put(ConfigSchema.Glass.BLUR.name() + "_tenths", 92);
        values.put(ConfigSchema.Glass.TINT_GREEN.name(), 100);
        values.put(ConfigSchema.LauncherHighlight.LARGE_CAUSTICS.name(), true);
        String menuBlur = ScopedGlassOptics.key(
                ScopedGlassOptics.HANDLE_MENU, ConfigSchema.Glass.BLUR);
        String menuLens = ScopedGlassOptics.key(
                ScopedGlassOptics.HANDLE_MENU, ConfigSchema.Glass.LENS_REFRACTION);
        String menuHighlight = ScopedGlassOptics.menuHighlightKey(
                ScopedGlassOptics.HANDLE_MENU, ConfigSchema.LauncherHighlight.LARGE_CAUSTICS);
        String popupHighlight = ScopedGlassOptics.menuHighlightKey(
                ScopedGlassOptics.POPUP, ConfigSchema.LauncherHighlight.LARGE_CAUSTICS);
        assertFalse(menuHighlight.equals(popupHighlight));
        assertTrue(menuBlur.startsWith("liquid_scope_handle_menu_"));
        values.put(menuBlur, 18);
        values.put(menuBlur + "_tenths", 185);
        values.put(menuLens + "_tenths", 24);
        values.put(menuHighlight, false);
        ConfigReader reader = new ConfigReader(values);
        LiquidDockConfig.Glass global = LiquidDockConfig.from(reader).glass;
        LiquidDockConfig.Glass topMenu = ScopedGlassOptics.resolve(
                reader, global, ScopedGlassOptics.HANDLE_MENU);
        LiquidDockConfig.Glass shortcut = ScopedGlassOptics.resolve(
                reader, global, ScopedGlassOptics.POPUP);
        assertEquals(9.2f, global.blur, 0.0001f);
        assertEquals(18.5f, topMenu.blur, 0.0001f);
        assertEquals(2.4f, topMenu.lensRefraction, 0.0001f);
        assertEquals(100, topMenu.tintG);
        assertFalse(topMenu.largeSurfaceHighlightProfile.caustics);
        assertTrue(global.largeSurfaceHighlightProfile.caustics);
        assertEquals(9.2f, shortcut.blur, 0.0001f);
        assertTrue(shortcut.largeSurfaceHighlightProfile.caustics);
        assertTrue(ScopedGlassOptics.keysForScope(ScopedGlassOptics.HANDLE_MENU)
                .contains(ConfigSchema.Glass.TINT_ALPHA));
    }

    @Test public void solidFillIsIndependentOfLegacyTintAndScopedOverrides() {
        Map<String, Object> values = new HashMap<>();
        values.put(ConfigSchema.Glass.TINT_ALPHA.name(), 160);
        values.put(ConfigSchema.Glass.BASE_COLOR_ALPHA.name(), 220);
        values.put(ConfigSchema.Glass.BASE_COLOR_RED.name(), 17);
        values.put(ScopedGlassOptics.key(ScopedGlassOptics.POPUP,
                ConfigSchema.Glass.BASE_COLOR_ALPHA), 255);
        ConfigReader reader = new ConfigReader(values);
        LiquidDockConfig.Glass global = LiquidDockConfig.from(reader).glass;
        LiquidDockConfig.Glass popup = ScopedGlassOptics.resolve(
                reader, global, ScopedGlassOptics.POPUP);
        LiquidDockConfig.Glass folder = ScopedGlassOptics.resolve(
                reader, global, ScopedGlassOptics.DIALOG);
        assertEquals(160, global.tintAlpha);
        assertEquals(160, popup.tintAlpha);
        assertEquals(220, global.baseColorAlpha);
        assertEquals(255, popup.baseColorAlpha);
        assertEquals(220, folder.baseColorAlpha);
        assertEquals(17, popup.baseColorR);
    }

    @Test public void popupInheritsGlobalChangesUntilOverridden() {
        Map<String, Object> values = new HashMap<>();
        values.put(ConfigSchema.Glass.BLUR.name(), 6);
        ConfigReader initial = new ConfigReader(values);
        LiquidDockConfig.Glass initialGlobal = LiquidDockConfig.from(initial).glass;
        assertTrue(ScopedGlassOptics.resolve(initial, initialGlobal, ScopedGlassOptics.POPUP)
                == initialGlobal);
        values.put(ConfigSchema.Glass.BLUR.name(), 12);
        ConfigReader revised = new ConfigReader(values);
        LiquidDockConfig.Glass revisedGlobal = LiquidDockConfig.from(revised).glass;
        assertEquals(12f, ScopedGlassOptics.resolve(
                revised, revisedGlobal, ScopedGlassOptics.POPUP).blur, 0.0001f);
    }

    @Test public void oneScopeDoesNotChangeAnotherOrGlobalOptics() {
        Map<String, Object> values = new HashMap<>();
        values.put(ConfigSchema.Glass.LENS_REFRACTION.name(), 1);
        values.put(ConfigSchema.Glass.LENS_REFRACTION.name() + "_tenths", 13);
        values.put(
                ScopedGlassOptics.key(ScopedGlassOptics.GBOARD,
                        ConfigSchema.Glass.LENS_REFRACTION) + "_tenths", 25);
        ConfigReader reader = new ConfigReader(values);
        LiquidDockConfig.Glass global = LiquidDockConfig.from(reader).glass;
        LiquidDockConfig.Glass gboard = ScopedGlassOptics.resolve(
                reader, global, ScopedGlassOptics.GBOARD);
        LiquidDockConfig.Glass search = ScopedGlassOptics.resolve(
                reader, global, ScopedGlassOptics.SEARCHBOX);
        LiquidDockConfig.Glass dialog = ScopedGlassOptics.resolve(
                reader, global, ScopedGlassOptics.DIALOG);
        assertEquals(1.3f, global.lensRefraction, 0.0001f);
        assertEquals(2.5f, gboard.lensRefraction, 0.0001f);
        assertEquals(1.3f, search.lensRefraction, 0.0001f);
        assertEquals(1.3f, dialog.lensRefraction, 0.0001f);
    }
}
