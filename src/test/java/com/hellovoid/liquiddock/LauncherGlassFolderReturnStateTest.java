package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class LauncherGlassFolderReturnStateTest {
    @Test public void beginRetainsSnapshotUntilExplicitClear() throws Exception {
        LauncherGlassFolderReturnState state = new LauncherGlassFolderReturnState();
        LauncherGlassGeometry.Snapshot snapshot = snapshot();

        state.begin(snapshot, 1728, 1080, false);

        assertTrue(state.isActive());
        assertSame(snapshot, state.geometryForRoot(1728, 1080));
        assertSame(snapshot, state.geometryForRoot(1728, 1080));
        assertTrue(state.clear());
        assertFalse(state.isActive());
        assertNull(state.geometryForRoot(1728, 1080));
        assertFalse(state.clear());
    }

    @Test public void rootSizeMismatchClearsCachedSnapshot() throws Exception {
        LauncherGlassFolderReturnState state = new LauncherGlassFolderReturnState();
        state.begin(snapshot(), 1728, 1080, false);

        assertNull(state.geometryForRoot(1080, 1728));
        assertFalse(state.isActive());
        assertNull(state.geometryForRoot(1728, 1080));
    }

    @Test public void nullBeginClearsExistingSnapshot() throws Exception {
        LauncherGlassFolderReturnState state = new LauncherGlassFolderReturnState();
        state.begin(snapshot(), 1728, 1080, false);

        state.begin(null, 1728, 1080, false);

        assertFalse(state.isActive());
        assertNull(state.geometryForRoot(1728, 1080));
    }

    @Test public void invalidRootBeginClearsExistingSnapshot() throws Exception {
        LauncherGlassFolderReturnState state = new LauncherGlassFolderReturnState();
        state.begin(snapshot(), 1728, 1080, false);

        state.begin(snapshot(), 0, 1080, false);

        assertFalse(state.isActive());
        assertNull(state.geometryForRoot(1728, 1080));
    }

    @Test public void nativeCloseFadeTracksProgressAndRetargeting() {
        LauncherGlassFolderReturnState state = new LauncherGlassFolderReturnState();
        state.begin(snapshot(), 1728, 1080, true);
        assertTrue(state.isActive());
        assertEquals(0f, state.alpha(), 0f);
        assertFalse(state.updateCloseProgress(1f));
        assertTrue(state.updateCloseProgress(0.05f));
        assertEquals(0.5f, state.alpha(), 0.001f);
        assertTrue(state.updateCloseProgress(0f));
        assertEquals(1f, state.alpha(), 0f);
        assertTrue(state.updateCloseProgress(0.15f));
        assertEquals(0f, state.alpha(), 0f);
        assertTrue(state.clear());
        assertEquals(1f, state.alpha(), 0f);
    }

    @Test public void regularReturnDoesNotTrackNativeCloseProgress() {
        LauncherGlassFolderReturnState state = new LauncherGlassFolderReturnState();
        state.begin(snapshot(), 1728, 1080, false);
        assertEquals(1f, state.alpha(), 0f);
        assertFalse(state.updateCloseProgress(0.05f));
        assertEquals(1f, state.alpha(), 0f);
    }

    private static LauncherGlassGeometry.Snapshot snapshot() {
        return LauncherGlassGeometry.resolveStatic(1728, 1080,
                20f, 30f, 120f, 130f, 16f);
    }

}
