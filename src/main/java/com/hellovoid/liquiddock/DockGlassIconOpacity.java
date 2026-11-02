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

    /**
     * Group hide controls only non-animated Dock icons. FloatingIcon proxy geometry and
     * its native progress own the animated icon's alpha independently; the background
     * group must never suppress it or apply a second fade.
     */
    static float resolve(float transitionAlpha, float nativeSourceAlpha,
            boolean proxyActive, float homeSceneAlpha) {
        float local = resolve(transitionAlpha, nativeSourceAlpha, proxyActive);
        float scene = clamp(homeSceneAlpha);
        // The actively animated icon is never governed by the group-hide switch.
        // Its own native source/geometry/return-progress alpha remains authoritative.
        return proxyActive ? local : local * scene;
    }

    private static float clamp(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
