package com.hellovoid.liquiddock;

/**
 * Bridges a semantic Security Center activity-authority switch into the existing scene and
 * zero-copy producer gates without adding timers or polling.
 *
 * <p>Project-owned lifecycle access stays typed so release R8 is free to rename, inline, merge,
 * and shrink coordinator/session internals. Vendor/framework reflection remains outside this
 * boundary.</p>
 */
final class SecurityCenterSourceAuthorityController {
    private SecurityCenterSourceAuthorityController() {}

    static boolean rollover(
            SecurityCenterGlassCoordinator coordinator,
            Object previousAuthority,
            Object currentAuthority) {
        return coordinator != null
                && coordinator.rolloverSourceAuthority(previousAuthority, currentAuthority);
    }
}
