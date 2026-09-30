package com.hellovoid.liquiddock;

/** Android-free handoff state when a fresh Workspace backdrop beats the static output Surface. */
final class LauncherGlassStaticFreshHandoffState {
    static final class Pending {
        final long generation;
        final long wallpaperGeneration;
        final boolean wallpaperAuthoritative;

        Pending(long generation, long wallpaperGeneration, boolean wallpaperAuthoritative) {
            this.generation = generation;
            this.wallpaperGeneration = wallpaperGeneration;
            this.wallpaperAuthoritative = wallpaperAuthoritative;
        }

        boolean ready() {
            return generation >= 0L;
        }

        static Pending none() {
            return new Pending(-1L, -1L, false);
        }
    }

    private long generation = -1L;
    private long wallpaperGeneration = -1L;
    private boolean wallpaperAuthoritative;

    synchronized void record(
            long generation, long wallpaperGeneration, boolean wallpaperAuthoritative) {
        this.generation = generation;
        this.wallpaperGeneration = wallpaperGeneration;
        this.wallpaperAuthoritative = wallpaperAuthoritative;
    }

    synchronized Pending pendingFor(long currentGeneration) {
        if (generation < 0L || generation != currentGeneration) return Pending.none();
        return new Pending(generation, wallpaperGeneration, wallpaperAuthoritative);
    }

    synchronized void clear() {
        generation = -1L;
        wallpaperGeneration = -1L;
        wallpaperAuthoritative = false;
    }
}
