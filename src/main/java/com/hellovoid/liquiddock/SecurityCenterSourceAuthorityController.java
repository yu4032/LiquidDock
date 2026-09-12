package com.hellovoid.liquiddock;

/**
 * Bridges semantic Security Center activity-authority changes into the coordinator. Project-owned
 * lifecycle access is typed so R8 may rename, inline, or merge private implementation members.
 */
final class SecurityCenterSourceAuthorityController {
    private SecurityCenterSourceAuthorityController() {}

    static boolean rollover(
            SecurityCenterGlassCoordinator coordinator,
            Object previousAuthority,
            Object currentAuthority) {
        if (coordinator == null) return false;
        return coordinator.rolloverSourceAuthority(previousAuthority, currentAuthority);
    }
}
