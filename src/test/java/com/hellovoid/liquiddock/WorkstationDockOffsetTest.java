package com.hellovoid.liquiddock;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.assertEquals;

public class WorkstationDockOffsetTest {
    @Test public void wholeDockTranslationDoesNotAccumulateAndFollowsNativeAnimation() {
        WorkstationDockTranslationState state = new WorkstationDockTranslationState();
        assertEquals(12f, state.apply(2f, true, 10f), 0f);
        assertEquals(12f, state.apply(12f, true, 10f), 0f);
        assertEquals(22f, state.apply(12f, true, 20f), 0f);
        assertEquals(120f, state.apply(100f, true, 20f), 0f);
        assertEquals(100f, state.apply(120f, false, 20f), 0f);
        assertEquals(100f, state.apply(100f, false, 20f), 0f);
    }

    @Test public void disablingDoesNotOverwriteANewNativeTranslation() {
        WorkstationDockTranslationState state = new WorkstationDockTranslationState();
        state.apply(0f, true, 10f);
        assertEquals(80f, state.apply(80f, false, 10f), 0f);
    }

    @Test public void portraitSettingsDoNotLeakIntoLandscapeDefaults() {
        LiquidDockConfig.Workstation config = new LiquidDockConfig.Workstation(new ConfigReader(Map.of(
                "workstation_dock_icon_horizontal_offset_tenths", 125,
                "workstation_dock_icon_top_offset_tenths", -35)));
        assertEquals(12.5f, config.iconHorizontalOffset, 0f);
        assertEquals(-3.5f, config.iconTopOffset, 0f);
        assertEquals(0f, config.iconLandscapeHorizontalOffset, 0f);
        assertEquals(0f, config.iconLandscapeTopOffset, 0f);
    }

    @Test public void landscapeSettingsDoNotChangePortraitOrWholeDockOffset() {
        LiquidDockConfig.Workstation config = new LiquidDockConfig.Workstation(new ConfigReader(Map.of(
                "workstation_dock_vertical_offset_tenths", 45,
                "workstation_dock_icon_horizontal_offset_tenths", 125,
                "workstation_dock_icon_landscape_horizontal_offset_tenths", -65,
                "workstation_dock_icon_landscape_vertical_offset_tenths", 85)));
        assertEquals(4.5f, config.dockVerticalOffset, 0f);
        assertEquals(12.5f, config.iconHorizontalOffset, 0f);
        assertEquals(-6.5f, config.iconLandscapeHorizontalOffset, 0f);
        assertEquals(8.5f, config.iconLandscapeTopOffset, 0f);
    }
}
