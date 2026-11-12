package com.hellovoid.liquiddock;

/**
 * One claimed producer may cross the native SetPassBlurSurface boundary once.
 * Vendor mutations are suppressed rather than re-parceling or replacing it.
 * A module-authorized null detach is always forwarded.
 */
final class PassBlurClaimSubmissionGuard {
    private boolean producerForwarded;

    synchronized boolean shouldSuppress(boolean moduleDetach, boolean claimedSurfaceRequested) {
        if (moduleDetach) return false;
        if (producerForwarded || !claimedSurfaceRequested) return true;
        producerForwarded = true; // claim ownership before JNI, including partial failures
        return false;
    }
}
