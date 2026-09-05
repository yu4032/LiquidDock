package com.hellovoid.liquiddock;

/** Android-free producer rollover/fresh-frame state for the zero-copy PassBlur renderer. */
final class ZeroCopyProducerRecoveryState {
    static final class Decision {
        final boolean accepted;
        final boolean clearFrameworkBinding;
        final boolean clearFrameAvailable;
        final boolean recreateProducer;
        final boolean requestBind;

        private Decision(
                boolean accepted,
                boolean clearFrameworkBinding,
                boolean clearFrameAvailable,
                boolean recreateProducer,
                boolean requestBind) {
            this.accepted = accepted;
            this.clearFrameworkBinding = clearFrameworkBinding;
            this.clearFrameAvailable = clearFrameAvailable;
            this.recreateProducer = recreateProducer;
            this.requestBind = requestBind;
        }

        static Decision none() {
            return new Decision(false, false, false, false, false);
        }

        static Decision rebind() {
            return new Decision(true, true, true, true, false);
        }

        static Decision bind() {
            return new Decision(true, false, false, false, true);
        }

        static Decision invalidateFrame() {
            return new Decision(true, false, true, false, false);
        }
    }

    private boolean rebindPending;
    private boolean freshFrame;
    private boolean activationExhausted;

    synchronized Decision onRebindRequested() {
        if (rebindPending) return Decision.none();
        rebindPending = true;
        freshFrame = false;
        activationExhausted = false;
        return Decision.rebind();
    }

    synchronized Decision onProducerRecreated() {
        if (!rebindPending) return Decision.none();
        return Decision.bind();
    }

    synchronized void onBindSucceeded() {
        rebindPending = false;
        activationExhausted = false;
    }

    synchronized void onBindExhausted() {
        rebindPending = false;
        freshFrame = false;
        activationExhausted = true;
    }

    synchronized void onRecreateFailed() {
        onBindExhausted();
    }

    synchronized Decision onGeometryInvalidated() {
        freshFrame = false;
        return Decision.invalidateFrame();
    }

    synchronized void onFreshFrameConsumed() {
        freshFrame = true;
    }

    synchronized void onTerminalFailure() {
        onBindExhausted();
    }

    synchronized void onShutdown() {
        rebindPending = false;
        freshFrame = false;
    }

    synchronized boolean isRebindPending() {
        return rebindPending;
    }

    synchronized boolean hasFreshFrame() {
        return freshFrame;
    }

    synchronized boolean isActivationExhausted() {
        return activationExhausted;
    }
}
