package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SecurityCenterSidebarDrawableGeometryContractTest {
    @Test
    public void animatedDrawableRectOverridesStaticMaterialBounds() {
        SecurityCenterSidebarDrawableGeometry.State state =
                new SecurityCenterSidebarDrawableGeometry.State();

        assertFalse(state.hasLiveGeometry());
        state.update(10f, 20f, 110f, 420f);

        assertTrue(state.hasLiveGeometry());
        SecurityCenterSidebarDrawableGeometry.Snapshot snapshot = state.snapshot();
        assertEquals(10f, snapshot.left, 0.001f);
        assertEquals(20f, snapshot.top, 0.001f);
        assertEquals(110f, snapshot.right, 0.001f);
        assertEquals(420f, snapshot.bottom, 0.001f);
    }

    @Test
    public void invalidDrawableRectDoesNotReplaceLastValidGeometry() {
        SecurityCenterSidebarDrawableGeometry.State state =
                new SecurityCenterSidebarDrawableGeometry.State();

        state.update(10f, 20f, 110f, 420f);
        state.update(Float.NaN, 0f, 10f, 10f);

        SecurityCenterSidebarDrawableGeometry.Snapshot snapshot = state.snapshot();
        assertEquals(10f, snapshot.left, 0.001f);
        assertEquals(20f, snapshot.top, 0.001f);
        assertEquals(110f, snapshot.right, 0.001f);
        assertEquals(420f, snapshot.bottom, 0.001f);
    }

    @Test
    public void newAnimationEpochDropsPreviousRectUntilCurrentAnimationPublishes() {
        SecurityCenterSidebarDrawableGeometry.State state =
                new SecurityCenterSidebarDrawableGeometry.State();
        state.update(10f, 20f, 110f, 420f);

        state.beginAnimationEpoch();

        assertFalse("reused TurboLayout must not carry the previous animation's intermediate rect",
                state.hasLiveGeometry());
        state.update(30f, 40f, 130f, 440f);
        assertTrue(state.hasLiveGeometry());
        SecurityCenterSidebarDrawableGeometry.Snapshot snapshot = state.snapshot();
        assertEquals(30f, snapshot.left, 0.001f);
        assertEquals(40f, snapshot.top, 0.001f);
        assertEquals(130f, snapshot.right, 0.001f);
        assertEquals(440f, snapshot.bottom, 0.001f);
    }

    @Test
    public void clearReturnsAuthorityToMaterialView() {
        SecurityCenterSidebarDrawableGeometry.State state =
                new SecurityCenterSidebarDrawableGeometry.State();
        state.update(10f, 20f, 110f, 420f);

        state.clear();

        assertFalse(state.hasLiveGeometry());
    }
}
