package com.hellovoid.liquiddock;

/**
 * Combines the OEM ShortcutIcon visibility fade with the existing native-progress-owned
 * FloatingIcon glass fade. A proxy owns its own opacity while the source ShortcutIcon is
 * hidden, so its alpha must never be multiplied by the hidden source's alpha.
 */
final class DockGlassIconOpacity {
    private DockGlassIconOpacity() {}

    static float resolve(float transitionAlpha, float nativeSourceAlpha, boolean proxyActive) {
        float transition = clamp(transitionAlpha);
        if (transition <= 0f) return 0f;
        return transition * (proxyActive ? 1f : clamp(nativeSourceAlpha));
    }

    private static float clamp(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
