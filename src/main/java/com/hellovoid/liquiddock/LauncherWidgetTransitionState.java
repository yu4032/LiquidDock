package com.hellovoid.liquiddock;

/**
 * Pure lifecycle state for one Workspace widget while MIUI temporarily owns its visual during
 * widget <-> app animation. The native widget may be alpha=0 while LiquidDock still needs the last
 * valid geometry; return reveal is authorized only by the matching fresh scene generation.
 */
final class LauncherWidgetTransitionState {
    private enum Phase {
        IDLE,
        LAUNCH_FADING_OUT,
        RETURN_WAITING_FRESH,
        RETURN_FADING_IN
    }

    private Phase phase = Phase.IDLE;
    private long expectedFreshGeneration = -1L;

    void beginLaunchFadeOut() {
        phase = Phase.LAUNCH_FADING_OUT;
        expectedFreshGeneration = -1L;
    }

    void finishLaunchFadeOut() {
        if (phase != Phase.LAUNCH_FADING_OUT) return;
        phase = Phase.IDLE;
        expectedFreshGeneration = -1L;
    }

    boolean isLaunchFadeOut() {
        return phase == Phase.LAUNCH_FADING_OUT;
    }

    void beginReturnWaitingFresh(long generation) {
        phase = Phase.RETURN_WAITING_FRESH;
        expectedFreshGeneration = generation;
    }

    boolean isReturnWaitingFresh() {
        return phase == Phase.RETURN_WAITING_FRESH;
    }

    boolean isReturnTransition() {
        return phase == Phase.RETURN_WAITING_FRESH || phase == Phase.RETURN_FADING_IN;
    }

    long expectedFreshGeneration() {
        return expectedFreshGeneration;
    }

    boolean onFreshFrame(long generation) {
        if (phase != Phase.RETURN_WAITING_FRESH
                || expectedFreshGeneration < 0L
                || generation != expectedFreshGeneration) {
            return false;
        }
        phase = Phase.RETURN_FADING_IN;
        expectedFreshGeneration = -1L;
        return true;
    }

    boolean isReturnFadeIn() {
        return phase == Phase.RETURN_FADING_IN;
    }

    void finishReturnFadeIn() {
        if (phase != Phase.RETURN_FADING_IN) return;
        phase = Phase.IDLE;
        expectedFreshGeneration = -1L;
    }

    void cancel() {
        phase = Phase.IDLE;
        expectedFreshGeneration = -1L;
    }

    boolean shouldRetainGeometry() {
        return phase != Phase.IDLE;
    }
}
