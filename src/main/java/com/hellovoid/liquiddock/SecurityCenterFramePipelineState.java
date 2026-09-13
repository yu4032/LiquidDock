package com.hellovoid.liquiddock;

/**
 * Android-free back-pressure state for Security Center source freshness and TextureView
 * presentation. At most one physical presentation may be outstanding. Fresh PassBlur acquisition
 * stays live independently, while geometry changes may reuse the latest normalized backdrop from
 * the same generation instead of waiting for another producer buffer.
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
        final boolean backdropUpdated;
        final long serial;
        final long generation;

        Submission(boolean accepted, boolean backdropUpdated, long serial, long generation) {
            this.accepted = accepted;
            this.backdropUpdated = backdropUpdated;
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

    private long cachedGeneration = -1L;
    private long cachedRevision;

    private long inFlightSerial = -1L;
    private long inFlightGeneration = -1L;
    private long inFlightRevision = -1L;

    private long presentedSerial = -1L;
    private long presentedGeneration = -1L;
    private long presentedRevision = -1L;

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

        SecurityCenterGlassMorphProbe.bindLatestFrame(latestGeneration, latestSerial);
        return new Offer(request, false, -1L,
                request ? sourceGeneration : -1L);
    }

    /**
     * Consumes one genuinely new normalized PassBlur backdrop. The source request is satisfied even
     * when an older physical presentation is still awaiting TextureView ACK; the newer backdrop is
     * retained as a cached revision and will be replayed after that ACK instead of stealing it.
     */
    synchronized Submission onFreshSource(long generation) {
        if (!sourceRequested
                || generation < 0L
                || generation != sourceGeneration
                || generation != latestGeneration
                || latestSerial < 0L) {
            return noneSubmission();
        }

        sourceRequested = false;
        sourceGeneration = -1L;
        cachedGeneration = generation;
        cachedRevision++;

        if (inFlightSerial >= 0L) {
            return new Submission(false, true, -1L, -1L);
        }
        return beginSubmission(cachedRevision, true);
    }

    /**
     * Reuses the already normalized backdrop for geometry-only presentation work. Reuse never
     * crosses generations, never creates a second physical in-flight presentation, and only runs
     * when either geometry or cached backdrop content is newer than the last presented frame.
     */
    synchronized Submission onCachedSource(long generation) {
        if (generation < 0L
                || generation != latestGeneration
                || generation != cachedGeneration
                || latestSerial < 0L
                || inFlightSerial >= 0L) {
            return noneSubmission();
        }

        boolean geometryChanged = presentedGeneration != latestGeneration
                || presentedSerial != latestSerial;
        boolean backdropChanged = presentedGeneration != latestGeneration
                || presentedRevision != cachedRevision;
        if (!geometryChanged && !backdropChanged) return noneSubmission();

        return beginSubmission(cachedRevision, false);
    }

    private Submission beginSubmission(long revision, boolean freshSource) {
        inFlightSerial = latestSerial;
        inFlightGeneration = latestGeneration;
        inFlightRevision = revision;
        if (freshSource) {
            SecurityCenterGlassMorphProbe.sourceAccepted(inFlightGeneration, inFlightSerial);
        }
        return new Submission(true, freshSource, inFlightSerial, inFlightGeneration);
    }

    /**
     * Consume exactly the serial that was submitted to TextureView. A physically presented frame
     * from the still-current generation is immediately eligible to reveal custom glass, even when
     * a newer geometry serial arrived while it was awaiting the Surface update. Serial freshness
     * only controls catch-up; it must not keep vendor material visible throughout a continuously
     * changing animation. A frame from an obsolete generation is consumed but never allowed to
     * reveal.
     */
    synchronized Presentation onPresented(long serial, long generation) {
        if (serial < 0L || generation < 0L
                || serial != inFlightSerial || generation != inFlightGeneration) {
            return new Presentation(false, false, -1L);
        }

        long revision = inFlightRevision;
        inFlightSerial = -1L;
        inFlightGeneration = -1L;
        inFlightRevision = -1L;

        presentedSerial = serial;
        presentedGeneration = generation;
        presentedRevision = revision;
        boolean current = generation == latestGeneration;

        boolean request = false;
        long nextGeneration = -1L;
        if (latestSerial >= 0L && latestGeneration >= 0L && !sourceRequested) {
            sourceRequested = true;
            sourceGeneration = latestGeneration;
            request = true;
            nextGeneration = sourceGeneration;
        }
        SecurityCenterGlassMorphProbe.frameAck(
                serial, generation, current, request, nextGeneration);
        return new Presentation(current, request, nextGeneration);
    }

    /** Cancels only an output-invalidated presentation whose Surface can no longer display it. */
    synchronized Presentation cancelPresentation(long serial) {
        if (serial < 0L || serial != inFlightSerial) {
            return new Presentation(false, false, -1L);
        }
        SecurityCenterGlassMorphProbe.presentationCancelled(serial, "output-invalidated");
        inFlightSerial = -1L;
        inFlightGeneration = -1L;
        inFlightRevision = -1L;
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
        cachedGeneration = -1L;
        cachedRevision = 0L;
        inFlightSerial = -1L;
        inFlightGeneration = -1L;
        inFlightRevision = -1L;
        presentedSerial = -1L;
        presentedGeneration = -1L;
        presentedRevision = -1L;
    }

    private static Offer noneOffer() {
        return new Offer(false, false, -1L, -1L);
    }

    private static Submission noneSubmission() {
        return new Submission(false, false, -1L, -1L);
    }
}
