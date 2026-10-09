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
