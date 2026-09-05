package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure contracts for WallpaperContentGeneration independent of Android runtime. */
public class LauncherWallpaperContentStateTest {
    @Test public void wallpaperChangesAdvanceGenerationWithoutSceneInputs() {
        LauncherWallpaperContentState state = new LauncherWallpaperContentState();
        long initial = state.generation();
        long first = state.onWallpaperChanged();
        long second = state.onWallpaperChanged();
        assertTrue(first > initial);
        assertTrue(second > first);
        assertEquals(second, state.generation());
    }

    @Test public void candidateWithoutPendingWallpaperChangeDoesNotRequestPulse() {
        LauncherWallpaperContentState state = new LauncherWallpaperContentState();
        long generation = state.generation();
        LauncherWallpaperContentState.Pulse candidate = state.onCandidateBoundary(generation);
        assertFalse(candidate.requested());
    }

    @Test public void candidateBoundaryRequestsAtMostOnePulsePerGeneration() {
        LauncherWallpaperContentState state = new LauncherWallpaperContentState();
        long generation = state.onWallpaperChanged();
        LauncherWallpaperContentState.Pulse first = state.onCandidateBoundary(generation);
        LauncherWallpaperContentState.Pulse duplicate = state.onCandidateBoundary(generation);
        assertTrue(first.requested());
        assertEquals(generation, first.generation);
        assertFalse(first.authoritative);
        assertFalse(duplicate.requested());
    }

    @Test public void authoritativeAfterConsumedCandidateRequestsSecondPulse() {
        LauncherWallpaperContentState state = new LauncherWallpaperContentState();
        long generation = state.onWallpaperChanged();
        LauncherWallpaperContentState.Pulse candidate = state.onCandidateBoundary(generation);
        LauncherWallpaperContentState.Pulse noFollowUp = state.onCandidateFrameConsumed(generation);
        LauncherWallpaperContentState.Pulse authoritative = state.onAuthoritativeBoundary(generation);
        LauncherWallpaperContentState.Pulse duplicate = state.onAuthoritativeBoundary(generation);
        assertTrue(candidate.requested());
        assertFalse(noFollowUp.requested());
        assertTrue(authoritative.requested());
        assertTrue(authoritative.authoritative);
        assertEquals(generation, authoritative.generation);
        assertFalse(duplicate.requested());
    }

    @Test public void authoritativeDuringCandidateFlightDefersUntilCandidateFrame() {
        LauncherWallpaperContentState state = new LauncherWallpaperContentState();
        long generation = state.onWallpaperChanged();
        state.onCandidateBoundary(generation);

        LauncherWallpaperContentState.Pulse deferred = state.onAuthoritativeBoundary(generation);
        LauncherWallpaperContentState.Pulse duplicateBoundary = state.onAuthoritativeBoundary(generation);
        LauncherWallpaperContentState.Pulse followUp = state.onCandidateFrameConsumed(generation);

        assertFalse(deferred.requested());
        assertFalse(duplicateBoundary.requested());
        assertTrue(followUp.requested());
        assertTrue(followUp.authoritative);
        assertEquals(generation, followUp.generation);
    }

    @Test public void authoritativeBeforeCandidateCannotConsumeCurrentGenerationSlot() {
        LauncherWallpaperContentState state = new LauncherWallpaperContentState();
        long stale = state.onWallpaperChanged();
        state.onCandidateBoundary(stale);
        long current = state.onWallpaperChanged();

        LauncherWallpaperContentState.Pulse unpairedAuthoritative =
                state.onAuthoritativeBoundary(current);
        LauncherWallpaperContentState.Pulse candidate = state.onCandidateBoundary(current);
        state.onCandidateFrameConsumed(current);
        LauncherWallpaperContentState.Pulse pairedAuthoritative =
                state.onAuthoritativeBoundary(current);

        assertFalse(unpairedAuthoritative.requested());
        assertTrue(candidate.requested());
        assertTrue(pairedAuthoritative.requested());
        assertTrue(pairedAuthoritative.authoritative);
    }

    @Test public void staleBoundariesCannotRequestPulseForNewerWallpaper() {
        LauncherWallpaperContentState state = new LauncherWallpaperContentState();
        long stale = state.onWallpaperChanged();
        long current = state.onWallpaperChanged();
        LauncherWallpaperContentState.Pulse staleCandidate = state.onCandidateBoundary(stale);
        LauncherWallpaperContentState.Pulse staleAuthoritative =
                state.onAuthoritativeBoundary(stale);
        LauncherWallpaperContentState.Pulse currentCandidate = state.onCandidateBoundary(current);
        assertFalse(staleCandidate.requested());
        assertFalse(staleAuthoritative.requested());
        assertTrue(currentCandidate.requested());
    }

    @Test public void onlyCurrentAuthoritativeFrameCanCommitGeneration() {
        LauncherWallpaperContentState state = new LauncherWallpaperContentState();
        long stale = state.onWallpaperChanged();
        state.onCandidateBoundary(stale);
        state.onCandidateFrameConsumed(stale);
        state.onAuthoritativeBoundary(stale);
        long current = state.onWallpaperChanged();
        state.onCandidateBoundary(current);
        state.onCandidateFrameConsumed(current);

        boolean staleCommit = state.onFrameCommitted(stale, true);
        boolean candidateCommit = state.onFrameCommitted(current, false);
        state.onAuthoritativeBoundary(current);
        boolean currentCommit = state.onFrameCommitted(current, true);

        assertFalse(staleCommit);
        assertFalse(candidateCommit);
        assertTrue(currentCommit);
        assertEquals(current, state.committedGeneration());
    }
}
