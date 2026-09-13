package com.hellovoid.liquiddock;

/**
 * Android-free back-pressure state for Security Center root capture and TextureView presentation.
 * At most one root source request and one submitted presentation may be outstanding. Geometry
 * changes coalesce to the latest serial, but a frame that has already been submitted to a
 * TextureView must reach its own Surface update acknowledgement before a replacement is armed.
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

        boolean request = false;
        if (generation > latestGeneration) {
            latestGeneration = generation;
            latestSerial = serial;

            if (inFlightSerial < 0L) {
                // A previous source request may still arrive, but it is no longer authoritative.
                // Retarget the logical source slot and explicitly request the current generation;
                // onFreshSource rejects the obsolete callback by generation.
                sourceRequested = true;
                sourceGeneration = latestGeneration;
                request = true;
            } else {
                // Never cancel a presentation after it may have reached eglSwapBuffers. Its next
                // SurfaceTexture update belongs to that exact serial. Arming the replacement early
                // would let the old physical update falsely acknowledge the new serial.
                sourceRequested = false;
                sourceGeneration = -1L;
            }
        } else {
            if (serial <= latestSerial) return noneOffer();
            latestSerial = serial;
            if (!sourceRequested && inFlightSerial < 0L) {
                sourceRequested = true;
                sourceGeneration = latestGeneration;
                request = true;
            }
        }

        return new Offer(request, false, -1L,
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

    /**
     * Consume exactly the serial that was submitted to TextureView. If newer geometry arrived while
     * it was awaiting the Surface update, the old pixels are acknowledged only as a completed
     * physical submission; they are not current enough to reveal/authorize custom material. The
     * latest logical geometry then receives exactly one new source request.
     */
    synchronized Presentation onPresented(long serial, long generation) {
        if (serial < 0L || generation < 0L
                || serial != inFlightSerial || generation != inFlightGeneration) {
            return new Presentation(false, false, -1L);
        }
        inFlightSerial = -1L;
        inFlightGeneration = -1L;
        boolean current = generation == latestGeneration && serial == latestSerial;

        boolean request = false;
        long nextGeneration = -1L;
        if (latestSerial >= 0L && latestGeneration >= 0L && !sourceRequested) {
            sourceRequested = true;
            sourceGeneration = latestGeneration;
            request = true;
            nextGeneration = sourceGeneration;
        }
        return new Presentation(current, request, nextGeneration);
    }

    /** Cancels only an output-invalidated presentation whose Surface can no longer display it. */
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
