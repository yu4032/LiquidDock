package com.hellovoid.liquiddock;

/**
 * Android-free owner state for Launcher HOME spring capture fencing.
 *
 * <p>The WindowElement + RectFSpringAnim pair is the lifecycle identity. Duplicate callbacks are
 * idempotent, a newer spring may supersede an older one without dropping the barrier, and either
 * the matching spring terminal or the matching WindowElement terminal may release it.</p>
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

    private Object activeOwner;
    private Object activeAnimation;

    synchronized Decision onHomeAnimationStarted(Object owner, Object animation) {
        if (owner == null || animation == null) return Decision.none();
        if (activeOwner == owner && activeAnimation == animation) return Decision.none();

        boolean alreadyArmed = activeAnimation != null;
        activeOwner = owner;
        activeAnimation = animation;
        return alreadyArmed ? Decision.none() : Decision.freeze();
    }

    synchronized Decision onAnimationRetargetedAway(Object owner, Object animation) {
        if (owner == null || animation == null
                || activeOwner != owner || activeAnimation != animation) {
            return Decision.none();
        }
        clear();
        return Decision.release();
    }

    synchronized Decision onHomeAnimationTerminal(Object animation) {
        if (animation == null || activeAnimation != animation) return Decision.none();
        clear();
        return Decision.release();
    }

    synchronized Decision onHomeOwnerTerminal(Object owner) {
        if (owner == null || activeOwner != owner) return Decision.none();
        clear();
        return Decision.release();
    }

    synchronized boolean isArmed() {
        return activeAnimation != null;
    }

    private void clear() {
        activeOwner = null;
        activeAnimation = null;
    }
}
