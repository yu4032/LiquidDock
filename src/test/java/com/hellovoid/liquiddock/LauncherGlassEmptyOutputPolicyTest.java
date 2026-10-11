package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class LauncherGlassEmptyOutputPolicyTest {
    @Test public void initialAndTransitionToEmptyMustPresentOnce() {
        LauncherGlassEmptyOutputPolicy state = new LauncherGlassEmptyOutputPolicy();
        assertFalse(state.canSkipEmptyFrame()); // Uninitialized output contents are undefined.
        state.onSwapCompleted(true);             // Existing Widget glass frame.
        assertFalse(state.canSkipEmptyFrame());
        state.onSwapCompleted(false);            // Glass nodes disappear: transparent clear.
        assertTrue(state.canSkipEmptyFrame());
        assertTrue(state.canSkipEmptyFrame());   // New source frames need no empty EGL swaps.
    }

    @Test public void visibleGeometryImmediatelyResumesNormalRendering() {
        LauncherGlassEmptyOutputPolicy state = new LauncherGlassEmptyOutputPolicy();
        state.onSwapCompleted(false);
        assertTrue(state.canSkipEmptyFrame());
        // Caller finds a drawable node and renders it (the empty-frame gate is not consulted).
        state.onSwapCompleted(true);
        assertFalse(state.canSkipEmptyFrame());
        state.onSwapCompleted(true);
        assertFalse(state.canSkipEmptyFrame());
    }

    @Test public void failedClearMayNotPublishAFalseBlankState() {
        LauncherGlassEmptyOutputPolicy state = new LauncherGlassEmptyOutputPolicy();
        // No onSwapCompleted(false) due to EGL failure.
        assertFalse(state.canSkipEmptyFrame());
    }

    @Test public void surfaceRecreationMustClearTransparentAgain() {
        LauncherGlassEmptyOutputPolicy state = new LauncherGlassEmptyOutputPolicy();
        state.onSwapCompleted(false);
        assertTrue(state.canSkipEmptyFrame());
        state.onSurfaceReset();
        assertFalse(state.canSkipEmptyFrame());
    }
}
