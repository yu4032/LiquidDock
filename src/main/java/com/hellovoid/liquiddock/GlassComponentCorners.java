package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigKey;

/** Component-owned radius overrides; negative values preserve the OEM radius. */
final class GlassComponentCorners {
    private GlassComponentCorners() {}

    static float readDp(ConfigReader reader, ConfigKey<Integer> key) {
        float value = reader.f(key.name(), key.runtimeFallbackDp());
        if (!Float.isFinite(value)) return key.runtimeFallbackDp();
        return Math.max(key.minInt(), Math.min(key.maxInt(), value));
    }

    /** Resolve in local coordinates, before the host's existing animation transform. */
    static float resolve(float requestedDp, float nativeRadiusPx, float density,
                         float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0) return 0f;
        float safeDensity = Float.isFinite(density) && density > 0 ? density : 1f;
        float radius = Float.isFinite(requestedDp) && requestedDp >= 0
                ? requestedDp * safeDensity : nativeRadiusPx;
        if (!Float.isFinite(radius)) return 0f;
        return Math.max(0f, Math.min(radius, Math.min(width, height) * .5f));
    }
}
