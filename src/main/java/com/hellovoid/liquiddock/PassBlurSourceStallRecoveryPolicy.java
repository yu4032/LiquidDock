package com.hellovoid.liquiddock;

/** Pure gate for a single bounded Recents source-starvation producer rollover. */
final class PassBlurSourceStallRecoveryPolicy {
    private PassBlurSourceStallRecoveryPolicy() {}

    static boolean shouldRollover(
            boolean validBinding, boolean rebindPending,
            long arrivals, long arrivalsAtArm, long bindEpoch, long epochAtArm,
            long nowMs, long nextAllowedMs) {
        return validBinding
                && !rebindPending
                && arrivals == arrivalsAtArm
                && bindEpoch == epochAtArm
                && nowMs >= nextAllowedMs;
    }
}
