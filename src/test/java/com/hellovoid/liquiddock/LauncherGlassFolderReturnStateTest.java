package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class LauncherGlassFolderReturnStateTest {
    @Test public void beginRetainsSnapshotUntilExplicitClear() throws Exception {
        LauncherGlassFolderReturnState state = new LauncherGlassFolderReturnState();
        LauncherGlassGeometry.Snapshot snapshot = snapshot();

        state.begin(snapshot, 1728, 1080);

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
        state.begin(snapshot(), 1728, 1080);

        assertNull(state.geometryForRoot(1080, 1728));
        assertFalse(state.isActive());
        assertNull(state.geometryForRoot(1728, 1080));
    }

    @Test public void nullBeginClearsExistingSnapshot() throws Exception {
        LauncherGlassFolderReturnState state = new LauncherGlassFolderReturnState();
        state.begin(snapshot(), 1728, 1080);

        state.begin(null, 1728, 1080);

        assertFalse(state.isActive());
        assertNull(state.geometryForRoot(1728, 1080));
    }

    @Test public void invalidRootBeginClearsExistingSnapshot() throws Exception {
        LauncherGlassFolderReturnState state = new LauncherGlassFolderReturnState();
        state.begin(snapshot(), 1728, 1080);

        state.begin(snapshot(), 0, 1080);

        assertFalse(state.isActive());
        assertNull(state.geometryForRoot(1728, 1080));
    }

    private static LauncherGlassGeometry.Snapshot snapshot() {
        return LauncherGlassGeometry.resolveStatic(1728, 1080,
                20f, 30f, 120f, 130f, 16f);
    }

}
