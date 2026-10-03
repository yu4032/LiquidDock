package com.hellovoid.liquiddock.config;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/** Free grid axes are current; legacy grid_profile is import-only migration input. */
public class GridProfileCodecTest {
    @Test
    public void freeGridAxesRoundTripWithoutLegacyProfile() {
        Map<String, Object> preferences = new HashMap<>();
        preferences.put(ConfigSchema.Grid.COLUMNS.name(), 7);
        preferences.put(ConfigSchema.Grid.ROWS.name(), 5);

        Map<String, Object> exported = ConfigCodec.exportValues(preferences);
        assertEquals(7, exported.get(ConfigSchema.Grid.COLUMNS.name()));
        assertEquals(5, exported.get(ConfigSchema.Grid.ROWS.name()));
        assertFalse(exported.containsKey("grid_profile"));

        Map<String, Object> imported = ConfigCodec.importValues(exported);
        assertEquals(7, imported.get(ConfigSchema.Grid.COLUMNS.name()));
        assertEquals(5, imported.get(ConfigSchema.Grid.ROWS.name()));
        assertFalse(imported.containsKey("grid_profile"));
    }

    @Test
    public void legacyProfileMigratesOnlyMissingAxes() {
        Map<String, Object> legacy = new HashMap<>();
        legacy.put("grid_profile", "10x6");
        assertEquals(10, ConfigCodec.importValues(legacy).get("grid_columns"));
        assertEquals(6, ConfigCodec.importValues(legacy).get("grid_rows"));

        legacy.put("grid_columns", 7);
        Map<String, Object> imported = ConfigCodec.importValues(legacy);
        assertEquals(7, imported.get("grid_columns"));
        assertEquals(6, imported.get("grid_rows"));
        assertFalse(imported.containsKey("grid_profile"));
    }
}
