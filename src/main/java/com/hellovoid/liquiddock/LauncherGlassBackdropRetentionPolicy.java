package com.hellovoid.liquiddock;

/**
 * Ownership policy for the already-prepared Workspace backdrop consumer.
 *
 * <p>Scene/source freshness belongs to the producer clock. A prepared Prismal backdrop belongs to
 * the consumer/render-domain clock and remains usable for geometry-only redraws until that render
 * domain or target is actually invalidated.
 */
final class LauncherGlassBackdropRetentionPolicy {
    enum Invalidation {
        SCENE_FRESHNESS,
        SOURCE_ENDPOINT,
        RENDER_DOMAIN,
        RENDER_TARGET
    }

    private LauncherGlassBackdropRetentionPolicy() {}

    static boolean preservesPreparedBackdrop(Invalidation invalidation) {
        return invalidation == Invalidation.SCENE_FRESHNESS
                || invalidation == Invalidation.SOURCE_ENDPOINT;
    }
}
