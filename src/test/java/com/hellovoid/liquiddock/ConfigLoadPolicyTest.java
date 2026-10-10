package com.hellovoid.liquiddock;

import org.junit.After;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigLoadPolicyTest {
    @After
    public void restoreWidgetGridState() {
        WidgetGridSizing.setCustomGridEnabled(false);
    }

    @Test
    public void loadingConfigDoesNotMutateWidgetGridRuntimeState() {
        WidgetGridSizing.setCustomGridEnabled(false);
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("grid_enabled", true);

        LiquidDockConfig.from(new ConfigReader(prefs));

        assertArrayEquals(new int[]{0, 0, 0, 0}, WidgetGridSizing.gridRect(
                0, 0, 1, 1, new int[]{0}, new int[]{0}, 100, 100, 0, 0));
    }

    @Test
    public void removedGridKeyDoesNotEnableCurrentGrid() {
        Map<String, Object> old = new HashMap<>();
        old.put("home_grid_8x4", true);
        old.put("dock_divider_width_dp", 10);
        LiquidDockConfig config = LiquidDockConfig.from(new ConfigReader(old));
        assertFalse(config.grid.enabled);
        assertFalse(config.divider.enabled);

        old.put("grid_enabled", true);
        old.put("dock_divider_enabled", true);
        config = LiquidDockConfig.from(new ConfigReader(old));
        assertTrue(config.grid.enabled);
        assertTrue(config.divider.enabled);
    }

    @Test
    public void productionSnapshotLoadingDoesNotOpenRemotePreferencesWriter() {
        Map<String, Object> values = new HashMap<>();
        values.put("liquiddock_enabled", false);
        TestSharedPreferences remote = new TestSharedPreferences(values);

        ConfigReader reader = ConfigReader.load(remote);

        assertEquals(false, reader.b("liquiddock_enabled", true));
        assertEquals(0, remote.editCount());
        assertEquals(0, remote.commitCount());
        assertEquals(values, remote.getAll());
    }
}
