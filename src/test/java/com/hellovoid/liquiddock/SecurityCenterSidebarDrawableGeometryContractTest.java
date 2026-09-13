package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class SecurityCenterSidebarDrawableGeometryContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

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
    public void clearReturnsAuthorityToMaterialView() {
        SecurityCenterSidebarDrawableGeometry.State state =
                new SecurityCenterSidebarDrawableGeometry.State();
        state.update(10f, 20f, 110f, 420f);

        state.clear();

        assertFalse(state.hasLiveGeometry());
    }

    @Test
    public void productionDockGeometryConsultsLiveSidebarDrawableAuthority() throws Exception {
        String geometry = Files.readString(MAIN.resolve("SecurityCenterGlassGeometry.java"));
        String prepare = Files.readString(MAIN.resolve("SecurityCenterEarlyPrepareHook.java"));
        String spec = Files.readString(MAIN.resolve("SecurityCenterHookSpec.java"));

        assertTrue(geometry.contains("SecurityCenterSidebarDrawableGeometry.overrideForCandidate("));
        assertTrue(prepare.contains("pending.type == ASSISTANT_GLOBAL_DOCK"));
        assertTrue(prepare.contains("SecurityCenterSidebarDrawableGeometry.bindDock(dock)"));
        assertTrue(spec.contains("SIDEBAR_BACKGROUND_RESOURCE"));
    }
}
