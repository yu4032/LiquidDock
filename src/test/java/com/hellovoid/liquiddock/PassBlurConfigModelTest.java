package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class PassBlurConfigModelTest {
    @Test
    public void glassConfigDefaultsPreserveCurrentPassBlurBehavior() {
        LiquidDockConfig config = LiquidDockConfig.from(new MapConfigSource(new HashMap<>()));
        assertEquals(100, config.glass.passBlurCaptureScalePercent);
        assertEquals(0, config.glass.passBlurRenderFps);
    }

    @Test
    public void glassConfigReadsPersistedPassBlurQualityControls() {
        Map<String, Object> values = new HashMap<>();
        values.put("liquid_passblur_capture_scale", 75);
        values.put("liquid_passblur_render_fps", 30);
        LiquidDockConfig config = LiquidDockConfig.from(new MapConfigSource(values));
        assertEquals(75, config.glass.passBlurCaptureScalePercent);
        assertEquals(30, config.glass.passBlurRenderFps);
    }

    @Test
    public void glassConfigClampsPassBlurQualityControls() {
        Map<String, Object> values = new HashMap<>();
        values.put("liquid_passblur_capture_scale", 10);
        values.put("liquid_passblur_render_fps", 120);
        LiquidDockConfig config = LiquidDockConfig.from(new MapConfigSource(values));
        assertEquals(50, config.glass.passBlurCaptureScalePercent);
        assertEquals(60, config.glass.passBlurRenderFps);
    }

    private static final class MapConfigSource implements LiquidDockConfig.Source {
        private final Map<String, Object> values;

        MapConfigSource(Map<String, Object> values) { this.values = values; }

        @Override public boolean b(String key, boolean def) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : def;
        }

        @Override public int i(String key, int def) {
            Object value = values.get(key);
            return value instanceof Number ? ((Number) value).intValue() : def;
        }

        @Override public float f(String key, float def) {
            Object value = values.get(key);
            return value instanceof Number ? ((Number) value).floatValue() : def;
        }

        @Override public String s(String key, String def) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : def;
        }

        @Override public boolean contains(String key) { return values.containsKey(key); }
    }
}
