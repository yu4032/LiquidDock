package com.hellovoid.liquiddock;

/**
 * Android-free back-pressure state for Security Center source freshness and TextureView
 * presentation. The first frame of each presentation/output epoch requires a physical TextureView
 * acknowledgement so vendor handoff cannot race an old buffer. Once that epoch is confirmed,
 * same-generation geometry/backdrop updates are latest-wins and cannot make a best-effort
 * SurfaceTexture callback a permanent liveness gate.
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
        final boolean awaitPresentationAck;
        final long serial;
        final long generation;
        final long revision;

        Submission(boolean accepted, boolean backdropUpdated, boolean awaitPresentationAck,
                long serial, long generation, long revision) {
            this.accepted = accepted;
            this.backdropUpdated = backdropUpdated;
            this.awaitPresentationAck = awaitPresentationAck;
            this.serial = serial;
            this.generation = generation;
            this.revision = revision;
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

    /** Only the unconfirmed handoff/output epoch is allowed to occupy this physical ACK slot. */
    private long inFlightSerial = -1L;
    private long inFlightGeneration = -1L;
    private long inFlightRevision = -1L;

    /** Generation whose current output set has already produced one exact physical ACK. */
    private long confirmedGeneration = -1L;

    /** Last frame successfully submitted/acknowledged for deduping cached geometry replay. */
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
                // Never cancel an unconfirmed presentation after it may have reached eglSwapBuffers.
                // Its next SurfaceTexture update belongs to that exact serial. Arming the replacement
                // early would let the old physical update falsely acknowledge the new generation.
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

    /**
     * Consumes one genuinely new normalized PassBlur backdrop. A fresh source can update the cached
     * revision while the one unconfirmed physical handoff is pending. After handoff confirmation,
     * fresh frames are not serialized behind TextureView update callbacks.
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
            return new Submission(false, true, false, -1L, -1L, -1L);
        }
        return beginSubmission(cachedRevision, true);
    }

    /**
     * Reuses the already normalized backdrop for geometry-only presentation work. Reuse never
     * crosses generations. Before physical handoff confirmation only one presentation may be
     * outstanding; after confirmation, latest geometry is allowed to supersede a missing steady
     * SurfaceTexture update callback.
     */
    synchronized Submission onCachedSource(long generation) {
        if (generation < 0L
                || generation != latestGeneration
                || generation != cachedGeneration
                || latestSerial < 0L
                || inFlightSerial >= 0L) {
            return noneSubmission();
        }

        boolean needsPhysicalProof = confirmedGeneration != latestGeneration;
        boolean geometryChanged = presentedGeneration != latestGeneration
                || presentedSerial != latestSerial;
        boolean backdropChanged = presentedGeneration != latestGeneration
                || presentedRevision != cachedRevision;
        if (!needsPhysicalProof && !geometryChanged && !backdropChanged) return noneSubmission();

        return beginSubmission(cachedRevision, false);
    }

    private Submission beginSubmission(long revision, boolean backdropUpdated) {
        boolean awaitPresentationAck = confirmedGeneration != latestGeneration;
        long serial = latestSerial;
        long generation = latestGeneration;
        if (awaitPresentationAck) {
            inFlightSerial = serial;
            inFlightGeneration = generation;
            inFlightRevision = revision;
        }
        return new Submission(true, backdropUpdated, awaitPresentationAck,
                serial, generation, revision);
    }

    /**
     * Consume exactly the first serial that was submitted for an unconfirmed presentation epoch.
     * Only this path can confirm a generation/output set and therefore authorize vendor handoff.
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
        if (current) confirmedGeneration = generation;

        long nextGeneration = requestLatestSourceIfIdle();
        return new Presentation(current, nextGeneration >= 0L, nextGeneration);
    }

    /**
     * Completes a steady-state submission after all EGL swaps succeeded. This deliberately does not
     * require TextureView's best-effort update callback: physical proof was already obtained for the
     * current generation/output set. The return value is a source generation to request, or -1.
     */
    synchronized long onSteadySubmitted(long serial, long generation, long revision) {
        if (serial < 0L || generation < 0L || revision < 0L
                || generation != latestGeneration
                || generation != confirmedGeneration) {
            return -1L;
        }
        presentedSerial = serial;
        presentedGeneration = generation;
        presentedRevision = revision;
        return requestLatestSourceIfIdle();
    }

    /** A newly created EGL/TextureView output must prove one frame even in the same generation. */
    synchronized void invalidatePresentationConfirmation() {
        confirmedGeneration = -1L;
    }

    /** Cancels only an output-invalidated unconfirmed presentation. */
    synchronized Presentation cancelPresentation(long serial) {
        if (serial < 0L || serial != inFlightSerial) {
            return new Presentation(false, false, -1L);
        }
        inFlightSerial = -1L;
        inFlightGeneration = -1L;
        inFlightRevision = -1L;
        long nextGeneration = requestLatestSourceIfIdle();
        return new Presentation(false, nextGeneration >= 0L, nextGeneration);
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
        confirmedGeneration = -1L;
        presentedSerial = -1L;
        presentedGeneration = -1L;
        presentedRevision = -1L;
    }

    private long requestLatestSourceIfIdle() {
        if (latestSerial < 0L || latestGeneration < 0L || sourceRequested) return -1L;
        sourceRequested = true;
        sourceGeneration = latestGeneration;
        return sourceGeneration;
    }

    private static Offer noneOffer() {
        return new Offer(false, false, -1L, -1L);
    }

    private static Submission noneSubmission() {
        return new Submission(false, false, false, -1L, -1L, -1L);
    }
}
