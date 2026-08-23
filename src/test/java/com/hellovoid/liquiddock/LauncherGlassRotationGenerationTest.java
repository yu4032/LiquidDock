package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Rotation must reject stale root geometry and roll the one-shot Workspace producer endpoint. */
public class LauncherGlassRotationGenerationTest {
    private static boolean matchesRoot(
            int rootWidth, int rootHeight, int surfaceWidth, int surfaceHeight,
            int left, int top, int right, int bottom) throws Exception {
        Class<?> gate = Class.forName(
                "com.hellovoid.liquiddock.LauncherGlassProducerGeometryGate");
        Method method = gate.getDeclaredMethod("matchesRoot",
                int.class, int.class, int.class, int.class,
                int.class, int.class, int.class, int.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null,
                rootWidth, rootHeight, surfaceWidth, surfaceHeight,
                left, top, right, bottom);
    }

    private static boolean requiresEndpointRollover(
            int previousRotation, int nextRotation, boolean surfaceChanged) throws Exception {
        Class<?> policy = Class.forName(
                "com.hellovoid.liquiddock.LauncherGlassProducerTransitionPolicy");
        Method method = policy.getDeclaredMethod("requiresEndpointRollover",
                int.class, int.class, boolean.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, previousRotation, nextRotation, surfaceChanged);
    }

    @Test public void staleOppositeOrientationSurfaceIsRejected() throws Exception {
        assertTrue(matchesRoot(1880, 3008, 1880, 3008, 0, 0, 0, 0));
        assertTrue(matchesRoot(3008, 1880, 3008, 1880, 0, 0, 0, 0));
        assertFalse(matchesRoot(3008, 1880, 1880, 3008, 0, 0, 0, 0));
        assertFalse(matchesRoot(1880, 3008, 3008, 1880, 0, 0, 0, 0));
    }

    @Test public void rotationChangeRequiresFreshProducerEndpoint() throws Exception {
        assertTrue(requiresEndpointRollover(0, 1, false));
        assertTrue(requiresEndpointRollover(1, 0, false));
        assertTrue(requiresEndpointRollover(1, 3, false));
        assertFalse(requiresEndpointRollover(1, 1, false));
        assertTrue(requiresEndpointRollover(1, 1, true));
    }

    @Test public void inPlaceResizePulsesOnlyAfterBufferSizeIsApplied() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java"));
        int start = source.indexOf("private boolean refreshProducerGeometryOnUi(View root)");
        int end = source.indexOf("private boolean postRender(", start);
        String method = source.substring(start, end);
        int resize = method.indexOf("input.setDefaultBufferSize");
        int mainPost = method.indexOf("mainHandler.post(() ->", resize);
        int pulse = method.indexOf("Miuix307PassBlurBridge.requestSingleUpdate", resize);
        assertTrue("resize must happen on render thread", resize >= 0);
        assertTrue("pulse must be posted only after resize returns", mainPost > resize);
        assertTrue("one-shot pulse must run after the resize barrier", pulse > mainPost);
    }
}
