package com.hellovoid.liquiddock;

/** Semantic output-space policy for vendor material roles. */
final class SecurityCenterSinkOutputPolicy {
    enum MaterialRole {
        DOCK,
        TOOLBOX,
        ALL_APPS
    }

    private SecurityCenterSinkOutputPolicy() {}

    static boolean usesRootSpaceOutput(MaterialRole role) {
        return role == MaterialRole.ALL_APPS;
    }
}
