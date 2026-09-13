package com.hellovoid.liquiddock;

/**
 * Optional activity/source-authority hint for Security Center PassBlur producer rollover.
 * Material View lifetime remains the primary presentation authority.
 */
final class SecurityCenterSourceAuthorityController {
    private static final String TAG = "[DC][SecurityCenterGlass]";

    private SecurityCenterSourceAuthorityController() {}

    static boolean rollover(
            SecurityCenterGlassCoordinator coordinator,
            Object previousAuthority,
            Object currentAuthority) {
        if (coordinator == null || previousAuthority == null || currentAuthority == null
                || previousAuthority.equals(currentAuthority)
                || !SecurityCenterGlassRuntimeState.isEnabled()) return false;
        try {
            return coordinator.onSourceAuthorityChanged(previousAuthority, currentAuthority);
        } catch (Throwable error) {
            log("source authority rollover failed closed", error);
            try { coordinator.releaseAll(); } catch (Throwable ignored) {}
            return false;
        }
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message + ": " + error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
