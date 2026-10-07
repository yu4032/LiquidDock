package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Guards allocation-free steady-state Workspace frame loops. */
public class LauncherGlassHotPathContractTest {
    private static final Path MAIN =
            Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void predrawAndStaticRenderUseLifecycleSnapshotsInsteadOfPerFrameCopies() throws Exception {
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        assertTrue(session.contains("private volatile NodeState[] nodeStateSnapshot"));
        assertTrue(session.contains("private volatile StaticNodeState[] staticNodeStateSnapshot"));
        assertTrue(session.contains("NodeState[] dragSnapshot = nodeStateSnapshot;"));
        assertTrue(session.contains("StaticNodeState[] staticSnapshot = staticNodeStateSnapshot;"));
        assertFalse(session.contains("new ArrayList<>(nodes.values())"));
        assertFalse(session.contains("new ArrayList<>(staticNodes.values())"));
        assertFalse(session.contains("new ArrayList<>(outputs.entrySet())"));
        assertTrue(session.contains("resolveStaticPrismalGeometry("));
        assertTrue(session.contains("state.renderedFrame == frame"));
    }

    @Test
    public void staticNodeStyleDoesNotAllocatePerGeometryCapture() throws Exception {
        String node = Files.readString(MAIN.resolve("LauncherGlassStaticNode.java"));
        assertTrue(node.contains("DEFAULT_COMPONENT_STYLE"));
        assertTrue(node.contains("DISABLED_COMPONENT_STYLE"));
        assertTrue(node.contains("return liveEnabled ? base : DISABLED_COMPONENT_STYLE;"));
    }
}
