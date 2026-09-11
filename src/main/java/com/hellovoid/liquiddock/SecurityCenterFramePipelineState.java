package com.hellovoid.liquiddock;

/**
 * Android-free back-pressure state for Security Center root capture and TextureView presentation.
 * At most one root source request and one presented serial may be outstanding for the current
 * generation. Same-generation geometry changes replace the pending serial instead of invalidating
 * root freshness repeatedly.
 */
final class SecurityCenterFramePipelineState {
    static final class Offer {
        final boolean requestSource;
        final boolean cancelPresentation;
        final long cancelledSerial;
        final long sourceGeneration;

        Offer(boolean requestSource, boolean cancelPresentation,
                long cancelledSerial, long sourceGeneration) {
            this.requestSource = requestSource;
            this.cancelPresentation = cancelPresentation;
            this.cancelledSerial = cancelledSerial;
            this.sourceGeneration = sourceGeneration;
        }
    }

    static final class Submission {
        final boolean accepted;
        final long serial;
        final long generation;

        Submission(boolean accepted, long serial, long generation) {
            this.accepted = accepted;
            this.serial = serial;
            this.generation = generation;
        }
    }

    static final class Presentation {
        final boolean acceptedCurrentGeneration;
        final boolean requestSource;
        final long nextGeneration;

        Presentation(boolean acceptedCurrentGeneration, boolean requestSource,
                long nextGeneration) {
            this.acceptedCurrentGeneration = acceptedCurrentGeneration;
            this.requestSource = requestSource;
            this.nextGeneration = nextGeneration;
        }
    }

    private long latestSerial = -1L;
    private long latestGeneration = -1L;
    private boolean sourceRequested;
    private long sourceGeneration = -1L;
    private long inFlightSerial = -1L;
    private long inFlightGeneration = -1L;

    synchronized Offer offer(long serial, long generation) {
        if (serial < 0L || generation < 0L) return noneOffer();
        if (latestGeneration >= 0L && generation < latestGeneration) return noneOffer();

        boolean cancel = false;
        long cancelled = -1L;
        if (generation > latestGeneration) {
            if (inFlightSerial >= 0L && inFlightGeneration < generation) {
                cancel = true;
                cancelled = inFlightSerial;
                inFlightSerial = -1L;
                inFlightGeneration = -1L;
            }
            sourceRequested = false;
            sourceGeneration = -1L;
            latestGeneration = generation;
            latestSerial = serial;
        } else {
            if (serial <= latestSerial) return noneOffer();
            latestSerial = serial;
        }

        boolean request = false;
        if (!sourceRequested && inFlightSerial < 0L) {
            sourceRequested = true;
            sourceGeneration = latestGeneration;
            request = true;
        }
        return new Offer(request, cancel, cancelled,
                request ? sourceGeneration : -1L);
    }

    synchronized Submission onFreshSource(long generation) {
        if (!sourceRequested || inFlightSerial >= 0L
                || generation < 0L
                || generation != sourceGeneration
                || generation != latestGeneration
                || latestSerial < 0L) {
            return new Submission(false, -1L, -1L);
        }
        sourceRequested = false;
        sourceGeneration = -1L;
        inFlightSerial = latestSerial;
        inFlightGeneration = latestGeneration;
        return new Submission(true, inFlightSerial, inFlightGeneration);
    }

    synchronized Presentation onPresented(long serial, long generation) {
        if (serial < 0L || generation < 0L
                || serial != inFlightSerial || generation != inFlightGeneration) {
            return new Presentation(false, false, -1L);
        }
        inFlightSerial = -1L;
        inFlightGeneration = -1L;
        boolean current = generation == latestGeneration;
        boolean request = false;
        long nextGeneration = -1L;
        if (current && latestSerial != serial && !sourceRequested) {
            sourceRequested = true;
            sourceGeneration = latestGeneration;
            request = true;
            nextGeneration = sourceGeneration;
        }
        return new Presentation(current, request, nextGeneration);
    }

    /** Cancels an output-invalidated presentation and schedules exactly one replacement source. */
    synchronized Presentation cancelPresentation(long serial) {
        if (serial < 0L || serial != inFlightSerial) {
            return new Presentation(false, false, -1L);
        }
        inFlightSerial = -1L;
        inFlightGeneration = -1L;
        boolean request = false;
        long nextGeneration = -1L;
        if (latestSerial >= 0L && latestGeneration >= 0L && !sourceRequested) {
            sourceRequested = true;
            sourceGeneration = latestGeneration;
            request = true;
            nextGeneration = sourceGeneration;
        }
        return new Presentation(false, request, nextGeneration);
    }

    /** Output creation may need to retry a source that arrived before the EGL output was ready. */
    synchronized long sourceGenerationToRetry() {
        return sourceRequested && inFlightSerial < 0L ? sourceGeneration : -1L;
    }

    synchronized void reset() {
        latestSerial = -1L;
        latestGeneration = -1L;
        sourceRequested = false;
        sourceGeneration = -1L;
        inFlightSerial = -1L;
        inFlightGeneration = -1L;
    }

    private static Offer noneOffer() {
        return new Offer(false, false, -1L, -1L);
    }
}
