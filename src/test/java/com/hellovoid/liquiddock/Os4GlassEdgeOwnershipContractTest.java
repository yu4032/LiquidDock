package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/**
 * Static edge-ownership contract: the zero-copy GlassHost must not own any Android foreground
 * drawable edge. OS4/Prismal is the only visual edge path; attempts to attach a foreground are
 * physically discarded instead of conditionally skipping its draw callback.
 */
public class Os4GlassEdgeOwnershipContractTest {
    private static final Path GLASS_HOST =
            Path.of("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java");

    @Test
    public void glassHostPhysicallyRejectsForegroundEdgeLayer() throws Exception {
        String source = Files.readString(GLASS_HOST);

        assertTrue(source.contains("public void setForeground(Drawable foreground)"));
        assertTrue(source.contains("super.setForeground(null);"));
        assertFalse(source.contains("public void onDrawForeground(Canvas canvas)"));
    }
}
