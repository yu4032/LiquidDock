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
        // The Sidebar Dock glass lives inside DockLayout so it inherits the vendor Folme
        // translation/scale/alpha directly. Root-space output would detach it from that animation
        // and force us to reconstruct the motion asynchronously.
        return role == MaterialRole.TOOLBOX
                || role == MaterialRole.ALL_APPS;
    }

    static boolean inheritsMaterialTransform(MaterialRole role) {
        return role == MaterialRole.DOCK || role == MaterialRole.DOCK_PREVIEW;
    }
}
