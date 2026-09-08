package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/**
 * Static edge-ownership contract: OS4 soft edge is the only bright foreground edge on the
 * zero-copy GlassHost. The saved configurable Dock stroke may remain installed, but the host must
 * not paint it while OS4 owns the edge.
 */
public class Os4GlassEdgeOwnershipContractTest {
    private static final Path GLASS_HOST =
            Path.of("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java");
    private static final Path GLASS_RUNTIME =
            Path.of("src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java");

    @Test
    public void glassHostSuppressesForegroundPaintingWhileOs4OwnsTheEdge() throws Exception {
        String source = Files.readString(GLASS_HOST);

        assertTrue(source.contains("public void onDrawForeground(Canvas canvas)"));
        assertTrue(source.contains("if (GlassRuntimeState.isOs4SoftEdgeEnabled()) return;"));
        assertTrue(source.contains("super.onDrawForeground(canvas);"));
    }

    @Test
    public void glassRuntimeTracksOs4EdgeOwnershipAndRefreshesStrokeOwners() throws Exception {
        String source = Files.readString(GLASS_RUNTIME);

        assertTrue(source.contains("ConfigSchema.Glass.OS4_SOFT_EDGE_ENABLED.name()"));
        assertTrue(source.contains("static boolean isOs4SoftEdgeEnabled()"));
        assertTrue(source.contains("DockStrokeRenderer.refreshInstalledFromCurrentConfig()"));
    }
}
