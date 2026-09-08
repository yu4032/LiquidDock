package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contract for eliminating Workspace page-scroll phase lag in the root-wide static glass layer. */
public class WorkspaceGlassScrollSyncContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path HOOK = MAIN.resolve("WorkspaceGlassScrollSyncHook.java");

    private static String source(Path path) throws Exception {
        assertTrue("missing " + path.getFileName(), Files.exists(path));
        return Files.readString(path);
    }

    @Test public void hooksExactHyperOs450WorkspaceScrollToBoundary() throws Exception {
        String hook = source(HOOK);
        assertTrue(hook.contains("com.miui.home.launcher.Workspace"));
        assertTrue(hook.contains("getDeclaredMethod(\"scrollTo\", int.class, int.class)"));
        assertTrue(hook.contains("beforeX"));
        assertTrue(hook.contains("afterX"));
        assertTrue(hook.contains("LauncherGlassSessionRegistry.syncWorkspaceScroll(workspace)"));
    }

    @Test public void scrollFastPathDoesNotAddFrameDelayOrProducerRefresh() throws Exception {
        String hook = source(HOOK);
        assertFalse(hook.contains("postOnAnimation"));
        assertFalse(hook.contains("postDelayed"));
        assertFalse(hook.contains("requestFreshBackdrop"));
        assertFalse(hook.contains("rebindProducer"));
    }

    @Test public void scrollFastPathUpdatesOnlyStaticGeometryBeforePredrawFallback() throws Exception {
        String registry = source(MAIN.resolve("LauncherGlassSessionRegistry.java"));
        String session = source(MAIN.resolve("LauncherGlassSession.java"));
        String moduleMain = source(MAIN.resolve("ModuleMain.java"));

        assertTrue(moduleMain.contains("WorkspaceGlassScrollSyncHook.install(classLoader)"));
        assertTrue(registry.contains("syncWorkspaceScroll(View workspace)"));
        assertTrue(registry.contains("session.syncStaticGeometryOnUiThread()"));
        assertTrue(session.contains("void syncStaticGeometryOnUiThread()"));
        assertTrue(session.contains("private boolean syncStaticGeometry(View root)"));
    }
}
