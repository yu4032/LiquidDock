package com.hellovoid.liquiddock.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PassBlurQualityConfigContractTest {
    @Test
    public void workspacePassBlurQualityControlsPreserveCurrentBehaviorByDefault() {
        ConfigKey<?> captureScale = findKey("liquid_passblur_capture_scale");
        assertEquals(Integer.valueOf(100), captureScale.uiDefault());
        assertEquals(Integer.valueOf(100), captureScale.runtimeFallback());
        assertEquals(Integer.valueOf(50), captureScale.minInt());
        assertEquals(Integer.valueOf(100), captureScale.maxInt());
        assertEquals(ConfigKey.ExportMode.ALWAYS, captureScale.exportMode());

        ConfigKey<?> renderFps = findKey("liquid_passblur_render_fps");
        assertEquals(Integer.valueOf(0), renderFps.uiDefault());
        assertEquals(Integer.valueOf(0), renderFps.runtimeFallback());
        assertEquals(Integer.valueOf(0), renderFps.minInt());
        assertEquals(Integer.valueOf(60), renderFps.maxInt());
        assertEquals(ConfigKey.ExportMode.ALWAYS, renderFps.exportMode());
    }

    private static ConfigKey<?> findKey(String name) {
        for (ConfigKey<?> key : ConfigSchema.all()) {
            if (name.equals(key.name())) return key;
        }
        throw new AssertionError("missing config key: " + name);
    }
}
