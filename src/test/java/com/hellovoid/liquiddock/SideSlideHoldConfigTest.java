package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SideSlideHoldConfigTest {
    @Test
    public void sideSlideHoldUsesDedicatedDisabledByDefaultPreference() {
        assertTrue("launcher450_side_slide_hold".equals(SideSlideHoldFeatureConfig.KEY));
        assertFalse(SideSlideHoldFeatureConfig.DEFAULT_ENABLED);
    }

    @Test
    public void featureConfigReadsDedicatedSwitch() {
        Map<String, Object> enabled = new HashMap<>();
        enabled.put(SideSlideHoldFeatureConfig.KEY, true);

        assertTrue(SideSlideHoldFeatureConfig.isEnabled(new ConfigReader(enabled)));
        assertFalse(SideSlideHoldFeatureConfig.isEnabled(new ConfigReader(new HashMap<>())));
    }
}
