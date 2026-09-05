package com.hellovoid.liquiddock;

/** Android-free authority state for Launcher fallback and SystemUI HOME transition handoff. */
final class HomeTransitionAuthorityState {
    static final class Decision {
        final boolean freezeBarrier;
        final boolean beginReveal;
        final boolean releaseBarrier;
        final boolean releaseWidgetBarrier;
        final boolean waitForSystemUi;

        private Decision(
                boolean freezeBarrier,
                boolean beginReveal,
                boolean releaseBarrier,
                boolean releaseWidgetBarrier,
                boolean waitForSystemUi) {
            this.freezeBarrier = freezeBarrier;
            this.beginReveal = beginReveal;
            this.releaseBarrier = releaseBarrier;
            this.releaseWidgetBarrier = releaseWidgetBarrier;
            this.waitForSystemUi = waitForSystemUi;
        }

        static Decision none() {
            return new Decision(false, false, false, false, false);
        }

        static Decision freeze(boolean beginReveal) {
            return new Decision(true, beginReveal, false, false, false);
        }

        static Decision reveal() {
            return new Decision(false, true, false, false, false);
        }

        static Decision release(boolean releaseWidgetBarrier) {
            return new Decision(false, false, true, releaseWidgetBarrier, false);
        }

        static Decision waitForSystemUi() {
            return new Decision(false, false, false, false, true);
        }
    }

    private boolean launcherHomeArmed;
    private boolean systemUiHomeArmed;
    private long activeSystemUiSerial = -1L;
    private long lastSystemUiEventTimeNanos = -1L;
    private long lastLauncherHomeEndTimeNanos = -1L;

    synchronized Decision onLauncherHomeStarted() {
        launcherHomeArmed = true;
        return Decision.freeze(false);
    }

    synchronized Decision onLauncherHomeAnimationStarted() {
        return launcherHomeArmed ? Decision.reveal() : Decision.none();
    }

    synchronized Decision onLauncherHomeEnded(long eventTimeNanos) {
        if (!launcherHomeArmed) return Decision.none();
        launcherHomeArmed = false;
        lastLauncherHomeEndTimeNanos = eventTimeNanos;
        return systemUiHomeArmed
                ? Decision.waitForSystemUi()
                : Decision.release(true);
    }

    synchronized Decision onSystemUiStarted(
            boolean homeVisible, long serial, long eventTimeNanos) {
        if (serial <= 0L || eventTimeNanos <= 0L
                || eventTimeNanos <= lastSystemUiEventTimeNanos) {
            return Decision.none();
        }
        if (homeVisible && eventTimeNanos <= lastLauncherHomeEndTimeNanos) {
            return Decision.none();
        }

        lastSystemUiEventTimeNanos = eventTimeNanos;
        if (!homeVisible) {
            if (!systemUiHomeArmed) return Decision.none();
            systemUiHomeArmed = false;
            activeSystemUiSerial = -1L;
            return Decision.release(false);
        }

        systemUiHomeArmed = true;
        activeSystemUiSerial = serial;
        return Decision.freeze(false);
    }

    synchronized Decision onSystemUiFinished(
            boolean homeVisible, long serial, long eventTimeNanos) {
        if (serial <= 0L || eventTimeNanos <= 0L
                || eventTimeNanos <= lastSystemUiEventTimeNanos) {
            return Decision.none();
        }
        lastSystemUiEventTimeNanos = eventTimeNanos;
        if (!homeVisible || !systemUiHomeArmed || serial != activeSystemUiSerial) {
            return Decision.none();
        }
        systemUiHomeArmed = false;
        activeSystemUiSerial = -1L;
        return Decision.release(true);
    }

    synchronized boolean isSystemUiAuthorityActive() {
        return systemUiHomeArmed;
    }
}
