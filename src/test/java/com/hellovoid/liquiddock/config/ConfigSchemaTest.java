package com.hellovoid.liquiddock.config;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigSchemaTest {
    @Test
    public void recentsBackgroundBlurIsAnExportedPercentage() {
        assertEquals(Integer.valueOf(100), ConfigSchema.Recents.BACKGROUND_BLUR_PERCENT.uiDefault());
        assertEquals(Integer.valueOf(0), ConfigSchema.Recents.BACKGROUND_BLUR_PERCENT.minInt());
        assertEquals(Integer.valueOf(100), ConfigSchema.Recents.BACKGROUND_BLUR_PERCENT.maxInt());
        assertTrue(ConfigSchema.all().contains(ConfigSchema.Recents.BACKGROUND_BLUR_PERCENT));
    }

    @Test
    public void dockFrameSyncIsEnabledByDefaultAndExported() {
        ConfigKey<Boolean> key = ConfigSchema.Dock.FRAME_SYNC;
        assertEquals("dock_frame_sync", key.name());
        assertEquals(Boolean.TRUE, key.uiDefault());
        assertEquals(Boolean.TRUE, key.runtimeFallback());
        assertEquals(Boolean.TRUE, key.exportDefault());
        assertEquals(ConfigKey.ExportMode.ALWAYS, key.exportMode());
        assertTrue(ConfigSchema.all().contains(key));
    }

    @Test
    public void gestureHandleHomeRecentsFadeIsOptInAndExported() {
        ConfigKey<Boolean> key = ConfigSchema.Animation.HIDE_GESTURE_HANDLE_HOME_RECENTS;
        assertEquals("animation_hide_gesture_handle_home_recents", key.name());
        assertEquals(Boolean.FALSE, key.uiDefault());
        assertEquals(Boolean.FALSE, key.runtimeFallback());
        assertEquals(Boolean.FALSE, key.exportDefault());
        assertEquals(ConfigKey.ExportMode.ALWAYS, key.exportMode());
        assertTrue(ConfigSchema.all().contains(key));
    }

    @Test
    public void launcherWallpaperZoomIsOptInAndExported() {
        ConfigKey<Boolean> key = ConfigSchema.Animation.DISABLE_WALLPAPER_ZOOM;
        assertEquals("animation_disable_wallpaper_zoom", key.name());
        assertEquals(Boolean.FALSE, key.uiDefault());
        assertEquals(Boolean.FALSE, key.runtimeFallback());
        assertEquals(Boolean.FALSE, key.exportDefault());
        assertEquals(ConfigKey.ExportMode.ALWAYS, key.exportMode());
        assertTrue(ConfigSchema.all().contains(key));
    }

    @Test
    public void recentsWallpaperDimmingSwitchPreservesVendorBehaviorByDefault() {
        assertEquals("recents_disable_wallpaper_dimming",
                ConfigSchema.Recents.DISABLE_WALLPAPER_DIMMING.name());
        assertEquals(Boolean.FALSE, ConfigSchema.Recents.DISABLE_WALLPAPER_DIMMING.uiDefault());
        assertEquals(Boolean.FALSE, ConfigSchema.Recents.DISABLE_WALLPAPER_DIMMING.runtimeFallback());
        assertEquals(ConfigKey.ExportMode.ALWAYS,
                ConfigSchema.Recents.DISABLE_WALLPAPER_DIMMING.exportMode());
        assertTrue(ConfigSchema.all().contains(ConfigSchema.Recents.DISABLE_WALLPAPER_DIMMING));
    }

    @Test
    public void systemUiHandleMenuGlassIsOptIn() {
        assertEquals("liquid_systemui_handle_menu_glass",
                ConfigSchema.Glass.SYSTEMUI_HANDLE_MENU_GLASS.name());
        assertEquals(Boolean.FALSE, ConfigSchema.Glass.SYSTEMUI_HANDLE_MENU_GLASS.uiDefault());
        assertEquals(Boolean.FALSE,
                ConfigSchema.Glass.SYSTEMUI_HANDLE_MENU_GLASS.runtimeFallback());
        assertEquals(ConfigKey.ExportMode.ALWAYS,
                ConfigSchema.Glass.SYSTEMUI_HANDLE_MENU_GLASS.exportMode());
        assertTrue(ConfigSchema.all().contains(ConfigSchema.Glass.SYSTEMUI_HANDLE_MENU_GLASS));
    }

    private static void assertComposeIntSpec(ConfigKey<Integer> key, int uiDefault,
                                             int min, int max) {
        assertEquals(key.name(), Integer.valueOf(uiDefault), key.uiDefault());
        assertEquals(key.name(), Integer.valueOf(min), key.minInt());
        assertEquals(key.name(), Integer.valueOf(max), key.maxInt());
    }

    @Test
    public void allKeyNamesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (ConfigKey<?> key : ConfigSchema.all()) {
            assertTrue("duplicate key: " + key.name(), seen.add(key.name()));
        }
    }

    @Test
    public void integerDefaultsAreInsideDeclaredImportBounds() {
        for (ConfigKey<?> key : ConfigSchema.all()) {
            if (key.type() != ConfigKey.Type.INT || key.minInt() == null) continue;
            int value = (Integer) key.uiDefault();
            assertTrue(key.name(), value >= key.minInt());
            assertTrue(key.name(), value <= key.maxInt());
        }
    }

    @Test
    public void legacyAndCurrentDefaultsRemainDistinctWhereRequired() {
        assertEquals(Integer.valueOf(4), ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH.runtimeFallback());
        assertEquals(Integer.valueOf(1), ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH.uiDefault());
        assertEquals(Integer.valueOf(4), ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH.exportDefault());
    }

    @Test
    public void currentGridEnableKeyHasNoEightByFourAlias() {
        assertEquals("grid_enabled", ConfigSchema.Grid.ENABLED.name());
        assertEquals(ConfigKey.ExportMode.ALWAYS, ConfigSchema.Grid.ENABLED.exportMode());
    }

    @Test
    public void composeGridSpecsKeepTheirCurrentDefaultsAndBounds() {
        assertComposeIntSpec(ConfigSchema.Grid.LANDSCAPE_HORIZONTAL_DISTANCE, 0, -600, 600);
        assertComposeIntSpec(ConfigSchema.Grid.LANDSCAPE_TOP_DISTANCE, 0, -600, 600);
        assertComposeIntSpec(ConfigSchema.Grid.LANDSCAPE_BOTTOM_DISTANCE, 0, -600, 600);
        assertComposeIntSpec(ConfigSchema.Grid.PORTRAIT_HORIZONTAL_DISTANCE, 0, -600, 600);
        assertComposeIntSpec(ConfigSchema.Grid.PORTRAIT_TOP_DISTANCE, 0, -600, 600);
        assertComposeIntSpec(ConfigSchema.Grid.PORTRAIT_BOTTOM_DISTANCE, 0, -600, 600);
        assertComposeIntSpec(ConfigSchema.Grid.LANDSCAPE_ROW_GAP, 0, -200, 400);
        assertComposeIntSpec(ConfigSchema.Grid.PORTRAIT_ROW_GAP, 0, -200, 400);
        assertComposeIntSpec(ConfigSchema.Grid.LANDSCAPE_INDICATOR_Y, 0, -160, 160);
        assertComposeIntSpec(ConfigSchema.Grid.PORTRAIT_INDICATOR_Y, 0, -160, 160);
    }

    @Test
    public void composeDockSpecsKeepTheirCurrentDefaultsAndBounds() {
        assertComposeIntSpec(ConfigSchema.Dock.BLUR_RADIUS, 100, 0, 400);
        assertComposeIntSpec(ConfigSchema.Dock.HEIGHT_OFFSET, 0, -80, 80);
        assertComposeIntSpec(ConfigSchema.Dock.WIDTH_OFFSET, 0, -80, 80);
        assertComposeIntSpec(ConfigSchema.Dock.CORNER_OFFSET, -1, -50, 100);
        assertComposeIntSpec(ConfigSchema.Dock.BLUR_CORNER_OFFSET, 0, -50, 100);
        assertComposeIntSpec(ConfigSchema.Dock.SPACING, 0, -8, 12);
        assertComposeIntSpec(ConfigSchema.Dock.BOTTOM_OFFSET, 0, -30, 40);
    }

    @Test
    public void composeDividerSpecsKeepTheirCurrentDefaultsAndBounds() {
        assertComposeIntSpec(ConfigSchema.Divider.WIDTH_DP, 10, 0, 160);
        assertComposeIntSpec(ConfigSchema.Divider.HEIGHT_SCALE, 60, 0, 100);
        assertComposeIntSpec(ConfigSchema.Divider.Y_OFFSET_DP, 0, -80, 80);
        assertComposeIntSpec(ConfigSchema.Divider.COLOR_RED, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Divider.COLOR_GREEN, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Divider.COLOR_BLUE, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Divider.ALPHA, 128, 0, 255);
    }

    @Test
    public void composeWorkstationSpecsKeepTheirCurrentDefaultsAndBounds() {
        assertComposeIntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET, 0, 0, 240);
        assertComposeIntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET, 0, 0, 240);
        assertComposeIntSpec(ConfigSchema.Workstation.DOCK_ICON_TOP_OFFSET, 0, -48, 48);
        assertComposeIntSpec(ConfigSchema.Workstation.DOCK_ICON_BOTTOM_OFFSET, 0, -48, 48);
    }

    @Test
    public void os4ReplacementRetiresUnusedOpticalKeysAndProfiles() {
        java.util.Set<String> removed = java.util.Set.of(
                "liquid_specular_sharp", "liquid_specular_strength",
                "liquid_rim_light", "liquid_prismal_plain_highlight",
                "liquid_edge_band", "liquid_highlight_alpha",
                "launcher_surface_component_specular",
                "launcher_surface_component_lit_rim",
                "launcher_surface_component_opposite_rim",
                "launcher_surface_component_corner_rim",
                "launcher_surface_component_face_sheen",
                "launcher_surface_component_plain_highlight",
                "launcher_large_surface_component_specular",
                "launcher_large_surface_component_lit_rim",
                "launcher_large_surface_component_opposite_rim",
                "launcher_large_surface_component_corner_rim",
                "launcher_large_surface_component_face_sheen",
                "launcher_large_surface_component_plain_highlight");
        for (ConfigKey<?> key : ConfigSchema.all()) {
            org.junit.Assert.assertFalse("obsolete schema key: " + key.name(),
                    removed.contains(key.name()));
        }
        for (String key : removed) {
            org.junit.Assert.assertFalse("obsolete preset key: " + key,
                    PresetManager.defaultValues().containsKey(key));
        }
    }

    @Test
    public void composeLiquidSpecsExposeOnlyActiveOpticalControls() {
        assertComposeIntSpec(ConfigSchema.Glass.BLUR, 2, 0, 60);
        assertComposeIntSpec(ConfigSchema.Glass.THICKNESS, 18, 1, 60);
        assertComposeIntSpec(ConfigSchema.Glass.IOR, 155, 100, 200);
        assertComposeIntSpec(ConfigSchema.Glass.NORMAL_STRENGTH, 115, 0, 300);
        assertComposeIntSpec(ConfigSchema.Glass.DOME, 130, 0, 200);
        assertComposeIntSpec(ConfigSchema.Glass.LENS_REFRACTION, 1, 0, 8);
        assertComposeIntSpec(ConfigSchema.Glass.DEPTH_EFFECT, 0, 0, 100);
        assertComposeIntSpec(ConfigSchema.Glass.CHROMATIC, 26, 0, 40);
        assertComposeIntSpec(ConfigSchema.Glass.TINT_ALPHA, 35, 0, 255);
        assertComposeIntSpec(ConfigSchema.Glass.TINT_RED, 0, 0, 255);
        assertComposeIntSpec(ConfigSchema.Glass.TINT_GREEN, 0, 0, 255);
        assertComposeIntSpec(ConfigSchema.Glass.TINT_BLUE, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Glass.HIGHLIGHT_WIDTH, 100, 50, 300);
        assertComposeIntSpec(ConfigSchema.Glass.BRIGHTNESS, 108, 50, 200);
        assertComposeIntSpec(ConfigSchema.Glass.CAUSTICS, 28, 0, 100);
    }

    @Test
    public void glassTintAlphaSupportsFullEightBitRangeWithoutChangingDefaults() {
        assertComposeIntSpec(ConfigSchema.Glass.TINT_ALPHA, 35, 0, 255);
        assertComposeIntSpec(ConfigSchema.Glass.OPEN_FOLDER_TINT_ALPHA, 35, 0, 255);
        assertEquals(Integer.valueOf(35), ConfigSchema.Glass.TINT_ALPHA.runtimeFallback());
        assertEquals(Integer.valueOf(35), ConfigSchema.Glass.OPEN_FOLDER_TINT_ALPHA.runtimeFallback());
    }

    @Test
    public void composePrismalSpecsUseUpstreamDefaultsAndDeclaredGuiBounds() {
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_REFRACTION_INSET, 20, 0, 80);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_DISPLACEMENT_SCALE, 115, 0, 400);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_HEIGHT_TRANSITION_WIDTH, 19, 1, 120);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_SMIN_SMOOTHING, 2, 0, 24);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_EDGE_REFRACTION_FALLOFF, 400, 0, 2000);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_FRESNEL_REFLECT, 198, 0, 500);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_DISPERSION_R, 100, 0, 400);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_DISPERSION_B, 100, 0, 400);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_VIBRANCY, 128, 0, 300);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X, -50, -200, 200);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_LIGHT_DIR_Y, -80, -200, 200);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_RED, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_GREEN, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_BLUE, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_ALPHA, 35, 0, 255);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_SOFTNESS, 1000, 0, 2000);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_TRANSMITTANCE, 100, 0, 100);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_X, 100, 25, 400);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_Y, 100, 25, 400);
        assertComposeIntSpec(ConfigSchema.Glass.PRISMAL_PARALLAX_SCALE, 100, 0, 400);
    }

    @Test
    public void composeStrokeSpecsKeepTheirCurrentDefaultsAndBounds() {
        assertComposeIntSpec(ConfigSchema.Dock.STROKE_RED, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Dock.STROKE_GREEN, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Dock.STROKE_BLUE, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Dock.STROKE_ALPHA, 255, 0, 255);
        assertComposeIntSpec(ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH, 1, 0, 10);
        assertComposeIntSpec(ConfigSchema.Dock.SQUIRCLE_STROKE_OFFSET, 3, 0, 16);
        assertComposeIntSpec(ConfigSchema.Dock.SQUIRCLE_CONTROL_POINT, 58, 40, 80);
        assertComposeIntSpec(ConfigSchema.Dock.FILL_DIFF_STROKE_WIDTH, 1, 0, 6);
        assertComposeIntSpec(ConfigSchema.Dock.STANDARD_STROKE_WIDTH, 1, 0, 10);
    }

    @Test
    public void composeShadowSpecsKeepTheirCurrentDefaultsAndBounds() {
        assertComposeIntSpec(ConfigSchema.Dock.SHADOW_RADIUS, 15, 1, 40);
        assertComposeIntSpec(ConfigSchema.Dock.SHADOW_SIZE, 18, 1, 60);
        assertComposeIntSpec(ConfigSchema.Dock.SHADOW_ALPHA, 140, 0, 200);
        assertComposeIntSpec(ConfigSchema.Dock.SHADOW_Y, 4, -24, 24);
        assertComposeIntSpec(ConfigSchema.Dock.STROKE_SHADOW_RADIUS, 3, 1, 24);
        assertComposeIntSpec(ConfigSchema.Dock.STROKE_SHADOW_ALPHA, 70, 0, 200);
    }

    @Test
    public void workspacePassBlurQualityControlsKeepCurrentBehaviorByDefault() {
        ConfigKey<?> captureScale = findKey("liquid_passblur_capture_scale");
        assertEquals(Integer.valueOf(100), captureScale.uiDefault());
        assertEquals(Integer.valueOf(100), captureScale.runtimeFallback());
        assertEquals(Integer.valueOf(50), captureScale.minInt());
        assertEquals(Integer.valueOf(100), captureScale.maxInt());
        assertEquals(ConfigKey.ExportMode.ALWAYS, captureScale.exportMode());

        ConfigKey<?> renderFps = findKey("liquid_passblur_render_fps");
        assertEquals(Integer.valueOf(0), renderFps.uiDefault());
        assertEquals(Integer.valueOf(0), renderFps.runtimeFallback());
        assertEquals(Integer.valueOf(0), renderFps.minInt());
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), renderFps.maxInt());
        assertEquals(ConfigKey.ExportMode.ALWAYS, renderFps.exportMode());
    }

    @Test
    public void obsoleteWorkstationDesktopOffsetCannotBeImportedOrExported() {
        String removedKey = "workstation_grid_horizontal_offset";
        java.util.Map<String, Object> oldPreferences = java.util.Map.of(
                removedKey, 120,
                ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET.name(), 24);
        assertFalse(ConfigSchema.all().stream().anyMatch(key -> key.name().equals(removedKey)));
        assertFalse(ConfigCodec.importValues(oldPreferences).containsKey(removedKey));
        assertFalse(ConfigCodec.exportValues(oldPreferences).containsKey(removedKey));
        // Removing the workspace-only offset must not affect existing All Apps / Dock settings.
        assertTrue(ConfigSchema.all().contains(
                ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET));
        assertTrue(ConfigSchema.all().contains(ConfigSchema.Workstation.DOCK_VERTICAL_OFFSET));
    }

    @Test
    public void obsoleteWorkstationDockWidthAndIconXOffsetsCannotAffectGeometry() {
        String[] removed = {
                "workstation_dock_width_offset",
                "workstation_dock_icon_horizontal_offset",
                "workstation_dock_icon_landscape_horizontal_offset"
        };
        java.util.Map<String, Object> oldPreferences = java.util.Map.of(
                "workstation_dock_width_offset", 26.5d,
                "workstation_dock_icon_horizontal_offset", 12.5d,
                "workstation_dock_icon_landscape_horizontal_offset", -6.5d);
        java.util.Map<String, Object> imported = ConfigCodec.importValues(oldPreferences);
        java.util.Map<String, Object> exported = ConfigCodec.exportValues(oldPreferences);
        for (String removedKey : removed) {
            assertFalse(removedKey, ConfigSchema.all().stream()
                    .anyMatch(key -> key.name().equals(removedKey)));
            assertFalse(removedKey, imported.containsKey(removedKey));
            assertFalse(removedKey, exported.containsKey(removedKey));
        }
        assertTrue(ConfigSchema.all().contains(ConfigSchema.Workstation.DOCK_VERTICAL_OFFSET));
        assertTrue(ConfigSchema.all().contains(ConfigSchema.Workstation.DOCK_ICON_TOP_OFFSET));
        assertTrue(ConfigSchema.all().contains(
                ConfigSchema.Workstation.DOCK_ICON_LANDSCAPE_VERTICAL_OFFSET));
    }

    private static ConfigKey<?> findKey(String name) {
        for (ConfigKey<?> key : ConfigSchema.all()) {
            if (name.equals(key.name())) return key;
        }
        throw new AssertionError("missing config key: " + name);
    }

}
