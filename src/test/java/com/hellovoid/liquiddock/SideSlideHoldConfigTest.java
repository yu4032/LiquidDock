package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SideSlideHoldConfigTest {
    @Test
    public void featureIsDedicatedAndDisabledByDefault() {
        assertTrue("launcher450_side_slide_hold".equals(SideSlideHoldFeatureConfig.KEY));
        assertFalse(SideSlideHoldFeatureConfig.DEFAULT_ENABLED);
    }

    @Test
    public void dedicatedPreferenceControlsFeature() {
        Map<String, Object> values = new HashMap<>();
        values.put(SideSlideHoldFeatureConfig.KEY, true);
        assertTrue(SideSlideHoldFeatureConfig.isEnabled(new ConfigReader(values)));
        assertFalse(SideSlideHoldFeatureConfig.isEnabled(new ConfigReader(new HashMap<>())));
    }
}
