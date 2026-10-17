package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class OpenFolderNativeMaterialTest {
    @Test public void presentationAndReleaseInvalidateTheActualMaterialOwner() throws Exception {
        OpenFolderGlassState state = new OpenFolderGlassState();
        Object policy = policy(state), material = new Object();
        long serial = state.prepare();
        int[] invalidations = {0};
        java.lang.reflect.Method track = policy.getClass().getDeclaredMethod("track", long.class, Object.class, Runnable.class);
        track.invoke(policy, serial, material, (Runnable) () -> invalidations[0]++);
        state.onBackdropReady(serial);
        state.beginOpen(serial);
        state.onPresented(serial);
        policy.getClass().getDeclaredMethod("invalidateAll").invoke(policy);
        assertEquals(1, invalidations[0]);
        assertTrue(suppress(policy, material));
        state.release();
        policy.getClass().getDeclaredMethod("invalidateAll").invoke(policy);
        assertEquals(2, invalidations[0]);
        assertFalse(suppress(policy, material));
    }

    @Test public void materialReplacementInvalidatesBothOwnersWithoutInvalidatingEveryFrame() throws Exception {
        OpenFolderGlassState state = new OpenFolderGlassState();
        Object policy = policy(state), old = new Object(), replacement = new Object();
        long serial = state.prepare();
        state.onBackdropReady(serial); state.beginOpen(serial); state.onPresented(serial);
        java.lang.reflect.Method track = policy.getClass().getDeclaredMethod("track", long.class, Object.class, Runnable.class);
        java.lang.reflect.Method end = policy.getClass().getDeclaredMethod("endFrame", long.class);
        int[] count = {0, 0};
        beginFrame(policy, serial);
        track.invoke(policy, serial, old, (Runnable) () -> count[0]++);
        end.invoke(policy, serial);
        assertEquals(1, count[0]);
        beginFrame(policy, serial);
        track.invoke(policy, serial, old, (Runnable) () -> count[0]++);
        end.invoke(policy, serial);
        assertEquals(1, count[0]);
        beginFrame(policy, serial);
        track.invoke(policy, serial, replacement, (Runnable) () -> count[1]++);
        end.invoke(policy, serial);
        assertEquals(2, count[0]);
        assertEquals(1, count[1]);
        assertFalse(suppress(policy, old));
        assertTrue(suppress(policy, replacement));
    }
    @Test public void closingRetainsIntermittentlyMissingNativeMaterials() throws Exception {
        OpenFolderGlassState state = new OpenFolderGlassState();
        Object policy = policy(state), expanded = new Object(), closingPlate = new Object();
        long serial = state.prepare();
        state.onBackdropReady(serial);
        state.beginOpen(serial);
        state.onPresented(serial);
        assertTrue(beginFrame(policy, serial));
        track(policy, serial, expanded);
        endFrame(policy, serial);
        assertTrue(state.beginClose(serial));
        assertTrue(beginFrame(policy, serial));
        track(policy, serial, closingPlate);
        endFrame(policy, serial);
        assertTrue("Native expanded plate can be shown again by the OEM spring",
                suppress(policy, expanded));
        assertTrue(suppress(policy, closingPlate));
        assertTrue(beginFrame(policy, serial));
        endFrame(policy, serial);
        assertTrue(suppress(policy, expanded));
        assertTrue(suppress(policy, closingPlate));
        state.release();
        assertFalse(suppress(policy, expanded));
        assertFalse(suppress(policy, closingPlate));
    }

    @Test public void nativeBackgroundRemainsUntilTheGlassIsActuallyPresented() throws Exception {
        OpenFolderGlassState state = new OpenFolderGlassState();
        Object policy = policy(state), background = new Object(), content = new Object();
        long serial = state.prepare();
        track(policy, serial, background);
        assertFalse(suppress(policy, background));
        state.onBackdropReady(serial);
        state.beginOpen(serial);
        assertFalse(suppress(policy, background));
        state.onPresented(serial);
        assertTrue(suppress(policy, background));
        assertFalse(suppress(policy, content));
        state.release();
        assertFalse(suppress(policy, background));
    }

    @Test public void staleFrameCannotClearTheNewFolderOrSuppressAnUnrelatedDrawable() throws Exception {
        OpenFolderGlassState state = new OpenFolderGlassState();
        Object policy = policy(state), oldBackground = new Object(), newBackground = new Object();
        long old = state.prepare();
        track(policy, old, oldBackground);
        long current = state.prepare();
        assertTrue(beginFrame(policy, current));
        track(policy, current, newBackground);
        state.onBackdropReady(current);
        state.beginOpen(current);
        state.onPresented(current);
        assertFalse(beginFrame(policy, old));
        assertTrue(suppress(policy, newBackground));
        assertFalse(suppress(policy, oldBackground));
    }

    @Test public void firstFrameFailureAndRepeatedCloseRestoreNativeDrawing() throws Exception {
        OpenFolderGlassState state = new OpenFolderGlassState();
        Object policy = policy(state), background = new Object();
        long serial = state.prepare();
        track(policy, serial, background);
        state.onBackdropReady(serial);
        state.beginOpen(serial);
        state.release();
        assertFalse(suppress(policy, background));
        assertFalse(state.onPresented(serial));
        assertFalse(state.finishClose(serial));
        assertFalse(suppress(policy, background));
    }

    private static Object policy(OpenFolderGlassState state) throws Exception {
        try { return Class.forName("com.hellovoid.liquiddock.OpenFolderNativeMaterial")
                .getDeclaredConstructor(OpenFolderGlassState.class).newInstance(state); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Missing draw-time material ownership", missing); }
    }
    private static boolean beginFrame(Object policy, long serial) throws Exception {
        return (Boolean) policy.getClass().getDeclaredMethod("beginFrame", long.class).invoke(policy, serial);
    }
    private static void endFrame(Object policy, long serial) throws Exception {
        policy.getClass().getDeclaredMethod("endFrame", long.class).invoke(policy, serial);
    }
    private static void track(Object policy, long serial, Object material) throws Exception {
        policy.getClass().getDeclaredMethod("track", long.class, Object.class).invoke(policy, serial, material);
    }
    private static boolean suppress(Object policy, Object material) throws Exception {
        return (Boolean) policy.getClass().getDeclaredMethod("shouldSuppress", Object.class).invoke(policy, material);
    }
}
