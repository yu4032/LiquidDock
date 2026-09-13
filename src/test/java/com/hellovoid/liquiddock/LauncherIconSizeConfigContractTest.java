package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Field;

import org.junit.Test;

public class LauncherIconSizeConfigContractTest {
    @Test
    public void gridSchemaOwnsLauncherIconSizeSwitchAndPercent() throws Exception {
        Class<?> grid = Class.forName("com.hellovoid.liquiddock.config.ConfigSchema$Grid");
        Field enabled = grid.getDeclaredField("ICON_SIZE_ENABLED");
        Field percent = grid.getDeclaredField("ICON_SIZE_PERCENT");
        assertNotNull(enabled.get(null));
        Object percentKey = percent.get(null);
        assertNotNull(percentKey);
        assertEquals("launcher_icon_size_percent",
                percentKey.getClass().getMethod("name").invoke(percentKey));
        assertEquals(100, ((Number) percentKey.getClass().getMethod("uiDefault").invoke(percentKey)).intValue());
        assertEquals(80, ((Number) percentKey.getClass().getMethod("minInt").invoke(percentKey)).intValue());
        assertEquals(120, ((Number) percentKey.getClass().getMethod("maxInt").invoke(percentKey)).intValue());
    }

    @Test
    public void runtimeGridCarriesOneSharedIconSizeValue() throws Exception {
        Class<?> grid = Class.forName("com.hellovoid.liquiddock.LiquidDockConfig$Grid");
        assertNotNull(grid.getDeclaredField("iconSizeEnabled"));
        assertNotNull(grid.getDeclaredField("iconSizePercent"));
    }
}
