package com.hellovoid.liquiddock;

import java.lang.reflect.Method;

/**
 * Experiment-only runtime overrides for Workspace PassBlur quality.
 *
 * <p>These are intentionally non-persisted Android debug properties rather than user preferences.
 * The default values reproduce main exactly. A Launcher restart/rebind is required after changing
 * them because capture scale and the consumer limiter are session-scoped.</p>
 */
final class PassBlurQualityRuntime {
    static final String CAPTURE_SCALE_PROPERTY = "debug.liquiddock.pb_scale";
    static final String RENDER_FPS_PROPERTY = "debug.liquiddock.pb_fps";

    private PassBlurQualityRuntime() {}

    static float workspaceCaptureScale() {
        return captureScaleFromRaw(readIntProperty(CAPTURE_SCALE_PROPERTY, 100));
    }

    static int workspaceRenderFps() {
        return renderFpsFromRaw(readIntProperty(RENDER_FPS_PROPERTY, 0));
    }

    static float captureScaleFromRaw(Integer rawPercent) {
        return PassBlurQualityPolicy.captureScale(rawPercent != null ? rawPercent : 100);
    }

    static int renderFpsFromRaw(Integer rawFps) {
        return PassBlurQualityPolicy.renderFps(rawFps != null ? rawFps : 0);
    }

    private static int readIntProperty(String name, int fallback) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method getInt = systemProperties.getDeclaredMethod(
                    "getInt", String.class, Integer.TYPE);
            getInt.setAccessible(true);
            Object value = getInt.invoke(null, name, fallback);
            return value instanceof Number ? ((Number) value).intValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
