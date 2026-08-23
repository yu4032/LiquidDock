package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Test;

/** Pure contracts for WallpaperContentGeneration independent of Android runtime. */
public class LauncherWallpaperContentStateTest {
    private static Object state() throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.hellovoid.liquiddock.LauncherWallpaperContentState");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("missing LauncherWallpaperContentState", missing);
        }
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static long generation(Object state) throws Exception {
        return (Long) call(state, "generation", new Class<?>[0]);
    }

    private static long committedGeneration(Object state) throws Exception {
        return (Long) call(state, "committedGeneration", new Class<?>[0]);
    }

    private static long pulseGeneration(Object pulse) throws Exception {
        Field field = pulse.getClass().getDeclaredField("generation");
        field.setAccessible(true);
        return field.getLong(pulse);
    }

    private static boolean pulseAuthoritative(Object pulse) throws Exception {
        Field field = pulse.getClass().getDeclaredField("authoritative");
        field.setAccessible(true);
        return field.getBoolean(pulse);
    }

    private static boolean pulseRequested(Object pulse) throws Exception {
        Method method = pulse.getClass().getDeclaredMethod("requested");
        method.setAccessible(true);
        return (Boolean) method.invoke(pulse);
    }

    @Test public void wallpaperChangesAdvanceGenerationWithoutSceneInputs() throws Exception {
        Object state = state();
        long initial = generation(state);
        long first = (Long) call(state, "onWallpaperChanged", new Class<?>[0]);
        long second = (Long) call(state, "onWallpaperChanged", new Class<?>[0]);
        assertTrue(first > initial);
        assertTrue(second > first);
        assertEquals(second, generation(state));
    }

    @Test public void candidateBoundaryRequestsAtMostOnePulsePerGeneration() throws Exception {
        Object state = state();
        long generation = (Long) call(state, "onWallpaperChanged", new Class<?>[0]);
        Object first = call(state, "onCandidateBoundary", new Class<?>[]{long.class}, generation);
        Object duplicate = call(state, "onCandidateBoundary", new Class<?>[]{long.class}, generation);
        assertTrue(pulseRequested(first));
        assertEquals(generation, pulseGeneration(first));
        assertFalse(pulseAuthoritative(first));
        assertFalse(pulseRequested(duplicate));
    }

    @Test public void authoritativeAfterConsumedCandidateRequestsSecondPulse() throws Exception {
        Object state = state();
        long generation = (Long) call(state, "onWallpaperChanged", new Class<?>[0]);
        Object candidate = call(state, "onCandidateBoundary", new Class<?>[]{long.class}, generation);
        Object noFollowUp = call(state, "onCandidateFrameConsumed",
                new Class<?>[]{long.class}, generation);
        Object authoritative = call(state, "onAuthoritativeBoundary",
                new Class<?>[]{long.class}, generation);
        Object duplicate = call(state, "onAuthoritativeBoundary",
                new Class<?>[]{long.class}, generation);
        assertTrue(pulseRequested(candidate));
        assertFalse(pulseRequested(noFollowUp));
        assertTrue(pulseRequested(authoritative));
        assertTrue(pulseAuthoritative(authoritative));
        assertEquals(generation, pulseGeneration(authoritative));
        assertFalse(pulseRequested(duplicate));
    }

    @Test public void authoritativeDuringCandidateFlightDefersUntilCandidateFrame()
            throws Exception {
        Object state = state();
        long generation = (Long) call(state, "onWallpaperChanged", new Class<?>[0]);
        call(state, "onCandidateBoundary", new Class<?>[]{long.class}, generation);

        Object deferred = call(state, "onAuthoritativeBoundary",
                new Class<?>[]{long.class}, generation);
        Object duplicateBoundary = call(state, "onAuthoritativeBoundary",
                new Class<?>[]{long.class}, generation);
        Object followUp = call(state, "onCandidateFrameConsumed",
                new Class<?>[]{long.class}, generation);

        assertFalse(pulseRequested(deferred));
        assertFalse(pulseRequested(duplicateBoundary));
        assertTrue(pulseRequested(followUp));
        assertTrue(pulseAuthoritative(followUp));
        assertEquals(generation, pulseGeneration(followUp));
    }

    @Test public void authoritativeBeforeCandidateCannotConsumeCurrentGenerationSlot()
            throws Exception {
        Object state = state();
        long stale = (Long) call(state, "onWallpaperChanged", new Class<?>[0]);
        call(state, "onCandidateBoundary", new Class<?>[]{long.class}, stale);
        long current = (Long) call(state, "onWallpaperChanged", new Class<?>[0]);

        Object unpairedAuthoritative = call(state, "onAuthoritativeBoundary",
                new Class<?>[]{long.class}, current);
        Object candidate = call(state, "onCandidateBoundary",
                new Class<?>[]{long.class}, current);
        call(state, "onCandidateFrameConsumed", new Class<?>[]{long.class}, current);
        Object pairedAuthoritative = call(state, "onAuthoritativeBoundary",
                new Class<?>[]{long.class}, current);

        assertFalse(pulseRequested(unpairedAuthoritative));
        assertTrue(pulseRequested(candidate));
        assertTrue(pulseRequested(pairedAuthoritative));
        assertTrue(pulseAuthoritative(pairedAuthoritative));
    }

    @Test public void staleBoundariesCannotRequestPulseForNewerWallpaper() throws Exception {
        Object state = state();
        long stale = (Long) call(state, "onWallpaperChanged", new Class<?>[0]);
        long current = (Long) call(state, "onWallpaperChanged", new Class<?>[0]);
        Object staleCandidate = call(state, "onCandidateBoundary",
                new Class<?>[]{long.class}, stale);
        Object staleAuthoritative = call(state, "onAuthoritativeBoundary",
                new Class<?>[]{long.class}, stale);
        Object currentCandidate = call(state, "onCandidateBoundary",
                new Class<?>[]{long.class}, current);
        assertFalse(pulseRequested(staleCandidate));
        assertFalse(pulseRequested(staleAuthoritative));
        assertTrue(pulseRequested(currentCandidate));
    }

    @Test public void onlyCurrentAuthoritativeFrameCanCommitGeneration() throws Exception {
        Object state = state();
        long stale = (Long) call(state, "onWallpaperChanged", new Class<?>[0]);
        call(state, "onCandidateBoundary", new Class<?>[]{long.class}, stale);
        call(state, "onCandidateFrameConsumed", new Class<?>[]{long.class}, stale);
        call(state, "onAuthoritativeBoundary", new Class<?>[]{long.class}, stale);
        long current = (Long) call(state, "onWallpaperChanged", new Class<?>[0]);
        call(state, "onCandidateBoundary", new Class<?>[]{long.class}, current);
        call(state, "onCandidateFrameConsumed", new Class<?>[]{long.class}, current);

        boolean staleCommit = (Boolean) call(state, "onFrameCommitted",
                new Class<?>[]{long.class, boolean.class}, stale, true);
        boolean candidateCommit = (Boolean) call(state, "onFrameCommitted",
                new Class<?>[]{long.class, boolean.class}, current, false);
        call(state, "onAuthoritativeBoundary", new Class<?>[]{long.class}, current);
        boolean currentCommit = (Boolean) call(state, "onFrameCommitted",
                new Class<?>[]{long.class, boolean.class}, current, true);

        assertFalse(staleCommit);
        assertFalse(candidateCommit);
        assertTrue(currentCommit);
        assertEquals(current, committedGeneration(state));
    }
}
