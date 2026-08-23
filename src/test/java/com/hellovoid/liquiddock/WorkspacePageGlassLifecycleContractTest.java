package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Device regression: pre-existing Workspace pages must join glass when they become current. */
public class WorkspacePageGlassLifecycleContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void currentWorkspacePageIsReconciledAtVendorPageCommitBoundary() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));

        assertTrue(hook.contains("installWorkspacePageReconcileHook"));
        assertTrue(hook.contains("com.miui.home.launcher.Workspace"));
        assertTrue(hook.contains("setCurrentScreenInner"));
        assertTrue(hook.contains("reconcileCurrentWorkspacePage"));
        assertTrue(hook.contains("getCurrentCellLayout"));
        assertTrue(hook.contains("postOnAnimation"));
    }

    @Test public void transientPageDetachDoesNotDisposeWorkspaceStaticNode() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));
        int detach = hook.indexOf("@Override public void onViewDetachedFromWindow(View v)");
        int close = hook.indexOf("};", detach);
        assertTrue(detach >= 0 && close > detach);
        String body = hook.substring(detach, close);

        assertTrue(body.contains("DockGlassItemRegistry.unregister(v)"));
        assertFalse(body.contains("node.dispose()"));
    }

    @Test public void vendorSnapshotPowerHasLiveBlurVisibilityFallback() throws Exception {
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));

        assertTrue(pipeline.contains("setMingouStaticDockSnapshotMode"));
        assertTrue(pipeline.contains("setMingouStaticDockLiveBlurVisible"));
        assertTrue(pipeline.contains("installVendorStaticDockLiveBlurPowerFallback"));
        assertTrue(pipeline.contains("Miuix307ZeroCopyRenderer.setProducerUpdatesEnabled(liveBlurVisible"));
    }
}
