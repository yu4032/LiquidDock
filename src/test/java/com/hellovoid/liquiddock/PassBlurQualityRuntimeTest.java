package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PassBlurQualityRuntimeTest {
    @Test
    public void missingExperimentalOverridesPreserveMainDefaults() {
        assertEquals(1.0f, PassBlurQualityRuntime.captureScaleFromRaw(null), 0.0001f);
        assertEquals(0, PassBlurQualityRuntime.renderFpsFromRaw(null));
    }

    @Test
    public void rawExperimentalOverridesUseSharedQualityPolicy() {
        assertEquals(0.50f, PassBlurQualityRuntime.captureScaleFromRaw(25), 0.0001f);
        assertEquals(0.75f, PassBlurQualityRuntime.captureScaleFromRaw(75), 0.0001f);
        assertEquals(1.0f, PassBlurQualityRuntime.captureScaleFromRaw(150), 0.0001f);
        assertEquals(0, PassBlurQualityRuntime.renderFpsFromRaw(-1));
        assertEquals(45, PassBlurQualityRuntime.renderFpsFromRaw(45));
        assertEquals(60, PassBlurQualityRuntime.renderFpsFromRaw(120));
    }
}
