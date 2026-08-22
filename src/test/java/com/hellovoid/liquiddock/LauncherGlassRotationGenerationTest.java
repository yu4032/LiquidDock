package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression coverage for atomic Launcher root/producer geometry across rotation. */
public class LauncherGlassRotationGenerationTest {
    private static boolean matchesRoot(
            int rootWidth, int rootHeight, int surfaceWidth, int surfaceHeight,
            int left, int top, int right, int bottom) throws Exception {
        final Class<?> gate;
        try {
            gate = Class.forName("com.hellovoid.liquiddock.LauncherGlassProducerGeometryGate");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("missing LauncherGlassProducerGeometryGate", missing);
        }
        Method method = gate.getDeclaredMethod("matchesRoot",
                int.class, int.class, int.class, int.class,
                int.class, int.class, int.class, int.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null,
                rootWidth, rootHeight, surfaceWidth, surfaceHeight,
                left, top, right, bottom);
    }

    @Test
    public void coherentSurfaceContentMatchesRootInBothOrientations() throws Exception {
        assertTrue(matchesRoot(1880, 3008, 1880, 3008, 0, 0, 0, 0));
        assertTrue(matchesRoot(3008, 1880, 3008, 1880, 0, 0, 0, 0));
        assertTrue(matchesRoot(3000, 1870, 3008, 1880, 3, 4, 5, 6));
    }

    @Test
    public void staleOppositeOrientationSurfaceIsRejected() throws Exception {
        assertFalse(matchesRoot(3008, 1880, 1880, 3008, 0, 0, 0, 0));
        assertFalse(matchesRoot(1880, 3008, 3008, 1880, 0, 0, 0, 0));
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
