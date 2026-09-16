package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contract for the preferred compositor-owned Gboard floating-glass output. */
public class GboardSurfaceControlGlassOutputContractTest {
    private static final Path OUTPUT = Path.of(
            "src/main/java/com/hellovoid/liquiddock/GboardSurfaceControlGlassOutput.java");

    @Test public void usesOnlyPublicAttachedSurfaceControlAndSurfaceControlApis() throws Exception {
        String source = Files.exists(OUTPUT) ? Files.readString(OUTPUT) : "";
        assertTrue(source.contains("implements GboardFloatingGlassOutput"));
        assertTrue(source.contains("AttachedSurfaceControl"));
        assertTrue(source.contains("getRootSurfaceControl()"));
        assertTrue(source.contains("buildReparentTransaction"));
        assertTrue(source.contains("new SurfaceControl.Builder()"));
        assertTrue(source.contains("new Surface(surfaceControl)"));
        assertTrue(source.contains("setPosition"));
        assertTrue(source.contains("setCrop"));
        assertTrue(source.contains("setBufferSize"));
        assertFalse(source.contains("setWindowCrop"));
        assertFalse(source.contains("getDeclared"));
        assertFalse(source.contains("setAccessible"));
    }

    @Test public void staysHiddenUntilCommittedFirstSwapAndReleasesAtomically() throws Exception {
        String source = Files.exists(OUTPUT) ? Files.readString(OUTPUT) : "";
        assertTrue(source.contains("setHidden(true)"));
        assertTrue(source.contains("addTransactionCommittedListener"));
        assertTrue(source.contains("listener.onPresented()"));
        assertTrue(source.contains("transaction.show(surfaceControl)"));
        assertTrue(source.contains("transaction.reparent(surfaceControl, null)"));
        assertTrue(source.contains("surface.release()"));
        assertTrue(source.contains("surfaceControl.release()"));
    }
}
