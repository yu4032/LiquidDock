package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SecurityCenterSidebarDrawableGeometryContractTest {
    @Test
    public void animatedDrawableGeometryOverridesStaticMaterialBounds() {
        SecurityCenterSidebarDrawableGeometry.State state =
                new SecurityCenterSidebarDrawableGeometry.State();

        assertFalse(state.hasLiveGeometry());
        state.update(10f, 20f, 110f, 420f, 36f);

        assertTrue(state.hasLiveGeometry());
        SecurityCenterSidebarDrawableGeometry.Snapshot snapshot = state.snapshot();
        assertEquals(10f, snapshot.left, 0.001f);
        assertEquals(20f, snapshot.top, 0.001f);
        assertEquals(110f, snapshot.right, 0.001f);
        assertEquals(420f, snapshot.bottom, 0.001f);
        assertEquals(36f, snapshot.radius, 0.001f);
    }

    @Test
    public void invalidDrawableGeometryDoesNotReplaceStableFallback() {
        SecurityCenterSidebarDrawableGeometry.State state =
                new SecurityCenterSidebarDrawableGeometry.State();

        state.update(10f, 20f, 110f, 420f, 36f);
        state.update(Float.NaN, 0f, 10f, 10f, 4f);

        SecurityCenterSidebarDrawableGeometry.Snapshot snapshot = state.snapshot();
        assertEquals(10f, snapshot.left, 0.001f);
        assertEquals(20f, snapshot.top, 0.001f);
        assertEquals(110f, snapshot.right, 0.001f);
        assertEquals(420f, snapshot.bottom, 0.001f);
        assertEquals(36f, snapshot.radius, 0.001f);
    }

    @Test
    public void clearReturnsAuthorityToMaterialView() {
        SecurityCenterSidebarDrawableGeometry.State state =
                new SecurityCenterSidebarDrawableGeometry.State();
        state.update(10f, 20f, 110f, 420f, 36f);

        state.clear();

        assertFalse(state.hasLiveGeometry());
    }
}
