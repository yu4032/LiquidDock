package com.hellovoid.liquiddock;

/** Android-free producer refresh state for Dock PassBlur across App -> HOME. */
final class DockHomeBackdropFreshnessState {
    static final class Decision {
        final boolean blockPresentation;
        final boolean forceProducerUpdates;
        final boolean releasePresentation;
        final boolean releaseProducerOverride;

        private Decision(
                boolean blockPresentation,
                boolean forceProducerUpdates,
                boolean releasePresentation,
                boolean releaseProducerOverride) {
            this.blockPresentation = blockPresentation;
            this.forceProducerUpdates = forceProducerUpdates;
            this.releasePresentation = releasePresentation;
            this.releaseProducerOverride = releaseProducerOverride;
        }

        static Decision none() {
            return new Decision(false, false, false, false);
        }

        static Decision forceFreshFrame() {
            return new Decision(false, true, false, false);
        }

        static Decision releaseOverride() {
            return new Decision(false, false, false, true);
        }
    }

    private long activeHomeSerial = -1L;
    private long producerFrameSerial;
    private long requiredFrameSerial = -1L;

    synchronized Decision onHomeStarted(long serial) {
        if (serial <= 0L) return Decision.none();
        activeHomeSerial = serial;
        requiredFrameSerial = producerFrameSerial + 1L;
        return Decision.forceFreshFrame();
    }

    synchronized Decision onHomeFinished(long serial) {
        if (serial <= 0L || serial != activeHomeSerial) return Decision.none();
        return Decision.none();
    }

    synchronized Decision onProducerFrameAvailable() {
        producerFrameSerial++;
        if (activeHomeSerial <= 0L || requiredFrameSerial <= 0L
                || producerFrameSerial < requiredFrameSerial) {
            return Decision.none();
        }
        activeHomeSerial = -1L;
        requiredFrameSerial = -1L;
        return Decision.releaseOverride();
    }

    synchronized void reset() {
        activeHomeSerial = -1L;
        requiredFrameSerial = -1L;
    }

    synchronized boolean isPresentationBlocked() {
        return false;
    }
}
