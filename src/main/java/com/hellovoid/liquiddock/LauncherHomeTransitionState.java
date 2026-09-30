package com.hellovoid.liquiddock;

/**
 * Android-free owner state for Launcher HOME capture fencing.
 *
 * <p>WindowElement + RectFSpringAnim are the lifecycle identity. A physical spring end is not by
 * itself a presentation terminal because Launcher may defer the shell end while a transition is
 * merged. The barrier releases only when the current HOME cycle completes through WindowElement,
 * is retargeted away from HOME, or is explicitly finished away from HOME.</p>
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
    private long activeCycle;
    private long finishRequestedCycle = -1L;
    private boolean springPhysicallyActive;

    synchronized Decision onHomeAnimationStarted(Object owner, Object animation) {
        if (owner == null || animation == null) return Decision.none();

        boolean barrierAlreadyArmed = activeAnimation != null;
        boolean duplicateCallback =
                activeOwner == owner && activeAnimation == animation && springPhysicallyActive;
        if (duplicateCallback) return Decision.none();

        activeOwner = owner;
        activeAnimation = animation;
        activeCycle++;
        finishRequestedCycle = -1L;
        springPhysicallyActive = true;
        return barrierAlreadyArmed ? Decision.none() : Decision.freeze();
    }

    synchronized Decision onAnimationRetargetedAway(Object owner, Object animation) {
        if (!matches(owner, animation)) return Decision.none();
        clear();
        return Decision.release();
    }

    synchronized void onSpringPhysicalTerminal(Object animation) {
        if (animation == null || activeAnimation != animation) return;
        springPhysicallyActive = false;
    }

    synchronized Decision onOwnerFinishRequested(Object owner, boolean toHome) {
        if (owner == null || activeOwner != owner || activeAnimation == null) {
            return Decision.none();
        }
        if (!toHome) {
            clear();
            return Decision.release();
        }
        finishRequestedCycle = activeCycle;
        return Decision.none();
    }

    synchronized Decision onOwnerFinishCompleted(Object owner) {
        if (owner == null || activeOwner != owner || activeAnimation == null
                || finishRequestedCycle != activeCycle) {
            return Decision.none();
        }
        clear();
        return Decision.release();
    }

    synchronized boolean isArmed() {
        return activeAnimation != null;
    }

    synchronized long activeCycle() {
        return activeCycle;
    }

    private boolean matches(Object owner, Object animation) {
        return owner != null && animation != null
                && activeOwner == owner && activeAnimation == animation;
    }

    private void clear() {
        activeOwner = null;
        activeAnimation = null;
        finishRequestedCycle = -1L;
        springPhysicallyActive = false;
    }
}
