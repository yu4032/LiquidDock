package com.hellovoid.liquiddock;

/** Semantic output-space policy for vendor material roles. */
final class SecurityCenterSinkOutputPolicy {
    enum MaterialRole {
        DOCK,
        DOCK_PREVIEW,
        TOOLBOX,
        ALL_APPS
    }

    private SecurityCenterSinkOutputPolicy() {}

    static boolean usesRootSpaceOutput(MaterialRole role) {
        // All Security Center shader outputs stay in root space. Dock/preview motion is sampled
        // synchronously from the real vendor carriers; making TextureView a child would transform
        // an already screen-space-rendered backdrop a second time.
        return role == MaterialRole.DOCK
                || role == MaterialRole.DOCK_PREVIEW
                || role == MaterialRole.TOOLBOX
                || role == MaterialRole.ALL_APPS;
    }

    static boolean inheritsMaterialTransform(MaterialRole role) {
        return false;
    }
}
