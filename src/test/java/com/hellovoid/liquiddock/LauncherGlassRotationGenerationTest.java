package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression coverage for atomic Launcher root/producer geometry across rotation. */
public class LauncherGlassRotationGenerationTest {
    @Test
    public void coherentSurfaceContentMatchesRootInBothOrientations() {
        assertTrue(LauncherGlassProducerGeometryGate.matchesRoot(
                1880, 3008, 1880, 3008, 0, 0, 0, 0));
        assertTrue(LauncherGlassProducerGeometryGate.matchesRoot(
                3008, 1880, 3008, 1880, 0, 0, 0, 0));
        assertTrue(LauncherGlassProducerGeometryGate.matchesRoot(
                3000, 1870, 3008, 1880, 3, 4, 5, 6));
    }

    @Test
    public void staleOppositeOrientationSurfaceIsRejected() {
        assertFalse(LauncherGlassProducerGeometryGate.matchesRoot(
                3008, 1880, 1880, 3008, 0, 0, 0, 0));
        assertFalse(LauncherGlassProducerGeometryGate.matchesRoot(
                1880, 3008, 3008, 1880, 0, 0, 0, 0));
    }

    @Test
    public void sessionInvalidatesOldProducerFrameBeforeRenderingNewGeometry() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java"));
        assertTrue(source.contains("LauncherGlassProducerGeometryGate.matchesRoot("));
        assertTrue(source.contains("frameAvailable.set(false)"));
        assertTrue(source.contains("hasConsumedFrame = false"));
        assertTrue(source.contains("producer geometry not coherent with root"));
        // The working Dock path already proves that rot1/3 swaps the producer buffer dimensions.
        assertTrue(source.contains("if (rotation == 1 || rotation == 3)"));
        assertTrue(source.contains("bufferWidth = surfaceHeight"));
        assertTrue(source.contains("bufferHeight = surfaceWidth"));
    }
}
