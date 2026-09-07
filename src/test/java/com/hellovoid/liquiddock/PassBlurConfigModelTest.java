package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigSchema;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class PassBlurConfigModelTest {
    @Test
    public void defaultsPreserveCurrentWorkspacePassBlurBehavior() {
        LiquidDockConfig config = LiquidDockConfig.from(
                new ConfigReader(Collections.emptyMap()));

        assertEquals(100, config.glass.passBlurCaptureScalePercent);
        assertEquals(0, config.glass.passBlurRenderFps);
    }

    @Test
    public void persistedWorkspacePassBlurQualityControlsReachTypedConfig() {
        Map<String, Object> values = new HashMap<>();
        values.put(ConfigSchema.Glass.PASSBLUR_CAPTURE_SCALE.name(), 75);
        values.put(ConfigSchema.Glass.PASSBLUR_RENDER_FPS.name(), 30);

        LiquidDockConfig config = LiquidDockConfig.from(new ConfigReader(values));

        assertEquals(75, config.glass.passBlurCaptureScalePercent);
        assertEquals(30, config.glass.passBlurRenderFps);
    }

    @Test
    public void typedConfigClampsPersistedPassBlurQualityValues() {
        Map<String, Object> values = new HashMap<>();
        values.put(ConfigSchema.Glass.PASSBLUR_CAPTURE_SCALE.name(), 10);
        values.put(ConfigSchema.Glass.PASSBLUR_RENDER_FPS.name(), 120);

        LiquidDockConfig config = LiquidDockConfig.from(new ConfigReader(values));

        assertEquals(50, config.glass.passBlurCaptureScalePercent);
        assertEquals(60, config.glass.passBlurRenderFps);
    }
}
