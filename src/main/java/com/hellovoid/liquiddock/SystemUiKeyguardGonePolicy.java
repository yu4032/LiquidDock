package com.hellovoid.liquiddock;

/** Pure policy for deciding when SystemUI has authoritatively completed device entry. */
final class SystemUiKeyguardGonePolicy {
    private static final String GONE = "GONE";
    private static final String FINISHED = "FINISHED";

    private SystemUiKeyguardGonePolicy() {}

    /**
     * A real transition into GONE is the semantic device-entry boundary regardless of which
     * keyguard surface was active immediately before it (lockscreen, bouncer, AOD, dozing, etc.).
     * GONE -> GONE is not a transition and must not re-release an old unlock barrier.
     */
    static boolean isGoneTransitionAttempt(String from, String to) {
        return GONE.equals(to) && from != null && !from.isEmpty() && !GONE.equals(from);
    }

    static boolean shouldPublishFinished(String from, String to, String transitionState) {
        return isGoneTransitionAttempt(from, to) && FINISHED.equals(transitionState);
    }
}
