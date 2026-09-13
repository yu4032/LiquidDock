package com.hellovoid.liquiddock;

/** Android-free coalescer for PassBlur producer refresh requests during Dock motion. */
final class DockBackdropProducerKickState {
    static final class Decision {
        final boolean postKick;

        private Decision(boolean postKick) {
            this.postKick = postKick;
        }

        static Decision none() {
            return new Decision(false);
        }

        static Decision post() {
            return new Decision(true);
        }
    }

    private boolean kickPending;

    synchronized Decision onGeometryChanged() {
        if (kickPending) return Decision.none();
        kickPending = true;
        return Decision.post();
    }

    synchronized void onKickConsumed() {
        kickPending = false;
    }

    synchronized void reset() {
        kickPending = false;
    }
}
