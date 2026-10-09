package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigSchema;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/** JVM coverage for key routing and typed snapshots; cross-process delivery needs a device. */
public class LiveGlassConfigStateTest {
    @Test
    public void glassOpticsAndTenthsAreLiveButUnrelatedGridKeysAreNot() {
        assertTrue(LiveGlassConfigState.isLiveKey(ConfigSchema.Glass.BLUR.name()));
        assertTrue(LiveGlassConfigState.isLiveKey(ConfigSchema.Glass.BLUR.name() + "_tenths"));
        assertTrue(LiveGlassConfigState.isLiveKey(ConfigSchema.Glass.IOR.name()));
        assertTrue(LiveGlassConfigState.isLiveKey(ConfigSchema.Glass.PRISMAL_DISPERSION_R.name()));
        assertTrue(LiveGlassConfigState.isLiveKey(ConfigSchema.LauncherHighlight.SPECULAR.name()));
        assertFalse(LiveGlassConfigState.isLiveKey(ConfigSchema.Grid.COLUMNS.name()));
        assertFalse(LiveGlassConfigState.isLiveKey("unrelated_setting"));
        assertTrue(LiveGlassConfigState.isLiveKey(null));
    }

    @Test
    public void completeOpticalFamiliesAndComponentGeometryAreRoutedLive() {
        assertTrue(LiveGlassConfigState.isLiveKey(ConfigSchema.Glass.OS4_EDGE_WIDTH_PX.name()));
        assertTrue(LiveGlassConfigState.isLiveKey(ConfigSchema.Glass.PRISMAL_SHOW_NORMALS.name()));
        assertTrue(LiveGlassConfigState.isLiveKey(ConfigSchema.Glass.PRISMAL_SHADOW_SOFTNESS.name()));
        assertTrue(LiveGlassConfigState.isLiveKey(
                ConfigSchema.Glass.WIDGET_CORNER_RADIUS.name() + "_tenths"));
        assertTrue(LiveGlassConfigState.isLiveKey(
                ConfigSchema.Glass.SMALL_FOLDER_SIZE_OFFSET.name() + "_tenths"));
        assertTrue(LiveGlassConfigState.isLiveKey(
                ConfigSchema.LauncherHighlight.LARGE_SPECULAR.name()));
    }

    @Test
    public void alreadyInstalledLauncherAnimationTimingsUpdateWithoutHookReload() {
        assertTrue(LiveGlassConfigState.isLiveKey(
                ConfigSchema.Animation.WORKSPACE_VISIBILITY.name()));
        assertTrue(LiveGlassConfigState.isLiveKey(
                ConfigSchema.Animation.PRESS_IN.name()));
        assertTrue(LiveGlassConfigState.isLiveKey(
                ConfigSchema.Animation.PRESS_OUT.name()));
        assertTrue(LiveGlassConfigState.isLiveKey(
                ConfigSchema.Animation.SHORTCUT_POPUP_DISMISS_FADE.name()));
        assertFalse(LiveGlassConfigState.isLiveKey(
                ConfigSchema.Animation.SETTINGS_PAGE.name()));

        Map<String, Object> values = new HashMap<>();
        values.put(ConfigSchema.Animation.PRESS_IN.name(), 125);
        values.put(ConfigSchema.Animation.PRESS_OUT.name(), 210);
        AnimationRuntimeState.configure(LiquidDockConfig.from(new ConfigReader(values)).animation);
        assertEquals(125, AnimationRuntimeState.pressInDurationMs());
        assertEquals(210, AnimationRuntimeState.pressOutDurationMs());
    }

    @Test
    public void hookInstallOnlyAndLegacyGlassKeysDoNotPretendToBeLive() {
        assertFalse(LiveGlassConfigState.isLiveKey(
                ConfigSchema.Glass.SHORTCUT_POPUP_GLASS.name()));
        assertFalse(LiveGlassConfigState.isLiveKey(
                ConfigSchema.Glass.SHORTCUT_POPUP_DARK_TEXT.name()));
        assertFalse(LiveGlassConfigState.isLiveKey(
                ConfigSchema.Glass.WALLPAPER_FLICKER_FIX.name()));
    }

    @Test
    public void recentsParametersAreLive() {
        assertTrue(LiveGlassConfigState.isLiveKey(
                ConfigSchema.Recents.BACKGROUND_BLUR_PERCENT.name()));
        assertTrue(LiveGlassConfigState.isLiveKey(
                ConfigSchema.Recents.DISABLE_WALLPAPER_DIMMING.name()));
    }

    @Test
    public void typedSnapshotRespectsDecimalBlurAndIndependentRecentsStrength() {
        Map<String, Object> values = new HashMap<>();
        values.put(ConfigSchema.Glass.BLUR.name(), 8);
        values.put(ConfigSchema.Glass.BLUR.name() + "_tenths", 125);
        values.put(ConfigSchema.Recents.BACKGROUND_BLUR_PERCENT.name(), 65);
        LiquidDockConfig config = LiquidDockConfig.from(new ConfigReader(values));
        assertEquals(12.5f, config.glass.blur, 0.0001f);
        assertEquals(65, config.recents.backgroundBlurPercent);
    }
}
