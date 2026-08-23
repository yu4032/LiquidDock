package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Idle Workspace pre-draws may poll cheap state, but must not rebuild established root-space geometry. */
public class LauncherGlassIdleWorkContractTest {
    private static final Path SESSION = Path.of(
            "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java");

    @Test public void unchangedEstablishedNodesSkipExpensiveGeometryCaptureButNewNodesCaptureOnce() throws Exception {
        String source = Files.readString(SESSION);

        assertTrue(source.contains("boolean localChanged = sink.syncFromMaterial();"));
        assertTrue(source.contains("boolean localChanged = node.syncFromMaterial();"));
        assertTrue("drag geometry must capture once when old geometry is null, then skip unchanged frames",
                source.contains("if (old != null && !rootGeometryChanged && !localChanged) continue;\n"
                        + "            LauncherGlassGeometry.Snapshot observed = sink.captureGeometry(root);"));
        assertTrue("static geometry must capture once when old geometry is null, then skip unchanged frames",
                source.contains("if (old != null && !rootGeometryChanged && !localChanged) continue;\n"
                        + "            LauncherGlassGeometry.Snapshot observed = node.captureGeometry(root);"));
    }

    @Test public void idlePreDrawDoesNotRequestGpuWorkByItself() throws Exception {
        String source = Files.readString(SESSION);
        int observerStart = source.indexOf("private void installRootObserver()");
        int observerEnd = source.indexOf("private void removeRootObserver()", observerStart);
        assertTrue(observerStart >= 0 && observerEnd > observerStart);
        String observer = source.substring(observerStart, observerEnd);

        assertTrue(observer.contains("syncSceneOnUiThread();"));
        assertFalse(observer.contains("requestFrame("));
        assertFalse(observer.contains("requestBackdropRebuild("));
        assertFalse(observer.contains("requestFresh"));
    }
}
