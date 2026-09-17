package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.ConfigKey;
import com.hellovoid.liquiddock.config.ConfigSchema;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SideSlideHoldConfigTest {
    @Test
    public void sideSlideHoldHasDedicatedDisabledByDefaultSwitch() {
        ConfigKey<Boolean> key = ConfigSchema.Core.SIDE_SLIDE_HOLD;

        assertEquals("launcher450_side_slide_hold", key.name());
        assertEquals(Boolean.FALSE, key.uiDefault());
        assertEquals(Boolean.FALSE, key.runtimeFallback());
        assertEquals(Boolean.FALSE, key.exportDefault());
        assertEquals(ConfigKey.ExportMode.ALWAYS, key.exportMode());
        assertTrue(ConfigSchema.all().contains(key));
    }

    @Test
    public void typedConfigReadsDedicatedSideSlideHoldSwitch() {
        Map<String, Object> enabled = new HashMap<>();
        enabled.put(ConfigSchema.Core.SIDE_SLIDE_HOLD.name(), true);

        assertTrue(LiquidDockConfig.from(new ConfigReader(enabled)).sideSlideHoldEnabled);
        assertFalse(LiquidDockConfig.from(new ConfigReader(new HashMap<>())).sideSlideHoldEnabled);
    }
}
