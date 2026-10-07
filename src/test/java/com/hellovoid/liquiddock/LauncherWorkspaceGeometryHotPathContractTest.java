package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Guards the Workspace pre-draw geometry path against O(nodes) root matrix inversion. */
public class LauncherWorkspaceGeometryHotPathContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void staticNodesShareOneRootInversePerPredraw() throws Exception {
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));
        String node = Files.readString(MAIN.resolve("LauncherGlassStaticNode.java"));

        assertTrue(session.contains("private final Matrix uiRootToGlobal = new Matrix();"));
        assertTrue(session.contains("private final Matrix uiGlobalToRoot = new Matrix();"));
        assertTrue(session.contains("prepareStaticRootCaptureTransform(root)"));
        assertTrue(session.contains(
                "node.captureGeometry(root, uiGlobalToRoot, rootWidth, rootHeight)"));

        int sharedStart = node.indexOf(
                "LauncherGlassGeometry.Snapshot captureGeometry(\n            View root, Matrix sharedGlobalToRoot");
        int sharedEnd = node.indexOf("private static int resolveWidgetSpanX", sharedStart);
        assertTrue(sharedStart >= 0);
        assertTrue(sharedEnd > sharedStart);
        String sharedCapture = node.substring(sharedStart, sharedEnd);
        assertTrue(sharedCapture.contains("sharedGlobalToRoot.mapPoints(geometryPoints);"));
        assertFalse(sharedCapture.contains("root.transformMatrixToGlobal(rootToGlobal)"));
        assertFalse(sharedCapture.contains("rootToGlobal.invert(globalToRoot)"));
    }
}
