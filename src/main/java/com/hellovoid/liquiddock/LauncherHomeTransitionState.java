package com.hellovoid.liquiddock;

/**
 * Android-free owner state for Launcher HOME spring capture fencing.
 *
 * <p>The vendor RectFSpringAnim object is the lifecycle identity. Duplicate listener callbacks for
 * the same spring are idempotent, a newer spring supersedes an older one without dropping the
 * barrier, and only the active spring may release it.</p>
 */
final class LauncherHomeTransitionState {
    static final class Decision {
        final boolean freezeBarrier;
        final boolean releaseBarrier;

        private Decision(boolean freezeBarrier, boolean releaseBarrier) {
            this.freezeBarrier = freezeBarrier;
            this.releaseBarrier = releaseBarrier;
        }

        static Decision none() {
            return new Decision(false, false);
        }

        static Decision freeze() {
            return new Decision(true, false);
        }

        static Decision release() {
            return new Decision(false, true);
        }
    }

    private Object activeAnimation;

    synchronized Decision onHomeAnimationStarted(Object animation) {
        if (animation == null) return Decision.none();
        if (activeAnimation == animation) return Decision.none();

        boolean alreadyArmed = activeAnimation != null;
        activeAnimation = animation;
        return alreadyArmed ? Decision.none() : Decision.freeze();
    }

    synchronized Decision onHomeAnimationTerminal(Object animation) {
        if (animation == null || activeAnimation != animation) return Decision.none();
        activeAnimation = null;
        return Decision.release();
    }

    synchronized boolean isArmed() {
        return activeAnimation != null;
    }
}
