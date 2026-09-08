package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigLoadPolicyTest {
    @Test
    public void loadingConfigKeepsWidgetAdaptationAsExplicitDecision() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("home_grid_8x4", true);
        prefs.put("grid_widget_adaptation", true);

        LiquidDockConfig config = LiquidDockConfig.from(new ConfigReader(prefs));

        assertTrue(WidgetGridSizing.shouldAdaptWidgets(
                config.grid.enabled, config.grid.widgetAdaptation));
        assertArrayEquals(new int[]{0, 0, 0, 0}, WidgetGridSizing.gridRect(
                false, 0, 0, 1, 1,
                new int[]{0}, new int[]{0}, 100, 100, 0, 0));
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
