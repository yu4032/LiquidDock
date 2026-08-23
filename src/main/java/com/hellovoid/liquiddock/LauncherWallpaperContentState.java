package com.hellovoid.liquiddock;

/**
 * Android-free state machine for Workspace wallpaper-content freshness.
 *
 * <p>Wallpaper content generation is intentionally independent from Launcher scene/surface/
 * geometry generations. A candidate UI boundary may request one early producer pulse, while a
 * later compositor-ready boundary may request one additional authoritative pulse for the same
 * content generation. Candidate and authoritative producer pulses are serialized so each
 * consumed OES frame has one unambiguous wallpaper-generation phase.</p>
 */
final class LauncherWallpaperContentState {
    static final class Pulse {
        private static final Pulse NONE = new Pulse(-1L, false, false);

        final long generation;
        final boolean authoritative;
        private final boolean requested;

        private Pulse(long generation, boolean authoritative, boolean requested) {
            this.generation = generation;
            this.authoritative = authoritative;
            this.requested = requested;
        }

        static Pulse request(long generation, boolean authoritative) {
            return new Pulse(generation, authoritative, true);
        }

        static Pulse none() {
            return NONE;
        }

        boolean requested() {
            return requested;
        }
    }

    private long generation;
    private long committedGeneration;
    private boolean candidateRequested;
    private boolean candidateFrameConsumed;
    private boolean authoritativeBoundarySeen;
    private boolean authoritativeRequested;

    synchronized long onWallpaperChanged() {
        generation++;
        candidateRequested = false;
        candidateFrameConsumed = false;
        authoritativeBoundarySeen = false;
        authoritativeRequested = false;
        return generation;
    }

    synchronized Pulse onCandidateBoundary(long eventGeneration) {
        if (eventGeneration != generation || candidateRequested || authoritativeRequested) {
            return Pulse.none();
        }
        candidateRequested = true;
        return Pulse.request(generation, false);
    }

    synchronized Pulse onAuthoritativeBoundary(long eventGeneration) {
        if (eventGeneration != generation || !candidateRequested
                || authoritativeBoundarySeen || authoritativeRequested) {
            return Pulse.none();
        }
        authoritativeBoundarySeen = true;
        if (!candidateFrameConsumed) return Pulse.none();
        authoritativeRequested = true;
        return Pulse.request(generation, true);
    }

    synchronized Pulse onCandidateFrameConsumed(long frameGeneration) {
        if (frameGeneration != generation || !candidateRequested || candidateFrameConsumed) {
            return Pulse.none();
        }
        candidateFrameConsumed = true;
        if (!authoritativeBoundarySeen || authoritativeRequested) return Pulse.none();
        authoritativeRequested = true;
        return Pulse.request(generation, true);
    }

    synchronized boolean onFrameCommitted(long frameGeneration, boolean authoritative) {
        if (frameGeneration != generation || !authoritative || !authoritativeRequested) {
            return false;
        }
        committedGeneration = generation;
        return true;
    }

    synchronized long generation() {
        return generation;
    }

    synchronized long committedGeneration() {
        return committedGeneration;
    }
}
