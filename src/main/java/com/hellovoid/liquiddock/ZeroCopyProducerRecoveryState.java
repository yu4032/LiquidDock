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

        static Decision rebind(boolean outputReady) {
            return new Decision(true, true, true, outputReady, false);
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
    private boolean waitingForOutput;

    synchronized Decision onRebindRequested() {
        return onRebindRequested(true);
    }

    synchronized Decision onRebindRequested(boolean outputReady) {
        if (rebindPending) return Decision.none();
        rebindPending = true;
        freshFrame = false;
        activationExhausted = false;
        waitingForOutput = !outputReady;
        return Decision.rebind(outputReady);
    }

    synchronized Decision onOutputReady() {
        if (!rebindPending || !waitingForOutput) return Decision.none();
        waitingForOutput = false;
        return new Decision(true, false, false, true, false);
    }

    synchronized void onOutputUnavailable() {
        if (rebindPending) waitingForOutput = true;
    }

    synchronized Decision onProducerRecreated() {
        if (!rebindPending) return Decision.none();
        return Decision.bind();
    }

    synchronized void onBindSucceeded() {
        waitingForOutput = false;
        rebindPending = false;
        activationExhausted = false;
    }

    synchronized void onBindExhausted() {
        waitingForOutput = false;
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
        waitingForOutput = false;
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
