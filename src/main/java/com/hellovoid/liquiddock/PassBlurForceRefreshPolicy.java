package com.hellovoid.liquiddock;

/** Policy for the vendor PassBlur force-refresh timeout discovered in HyperOS SurfaceFlinger. */
final class PassBlurForceRefreshPolicy {
    static final int PASSBLUR_FORCE_REFRESH_MS = 60_000;

    private PassBlurForceRefreshPolicy() {}

    static int timeoutMs(PassBlurDomain domain) {
        return domain == PassBlurDomain.DOCK ? PASSBLUR_FORCE_REFRESH_MS : -1;
    }
}
