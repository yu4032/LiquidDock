package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class OpenFolderMotionStateTest {
    @Test public void contentCompletionDoesNotFinishTheSlowerBackgroundSpring() throws Exception {
        Object state = state();
        assertEquals(.8f, sample(state, .8f), .0001f);
        call(state, "onContentFinished");
        assertEquals(.8f, sample(state, Float.NaN), .0001f);
        assertEquals(.9f, sample(state, .9f), .0001f);
        assertEquals(1f, sample(state, 1f), .0001f);
        assertEquals(1f, sample(state, 0f), .0001f);
    }
    @Test public void interruptedOpenContractsFromItsCurrentSizeRatherThanJumpingToFullSize() throws Exception {
        Object state = state();
        sample(state, .6f);
        call(state, "beginClose");
        assertEquals(.6f, sample(state, 1f), .0001f);
        assertEquals(.5f, sample(state, .5f), .0001f);
        call(state, "onContentFinished");
        assertEquals(.5f, sample(state, Float.NaN), .0001f);
        assertEquals(0f, sample(state, 0f), .0001f);
    }
    @Test public void completedOpenReturnsAlongTheSameNativeSpring() throws Exception {
        Object state = state();
        assertEquals(1f, sample(state, 1f), .0001f);
        call(state, "beginClose");
        assertEquals(1f, sample(state, 1f), .0001f);
        assertEquals(.85f, sample(state, .85f), .0001f);
        assertEquals(.5f, sample(state, .5f), .0001f);
        assertEquals(.5f, sample(state, .9f), .0001f);
        assertEquals(0f, sample(state, 0f), .0001f);
    }
    @Test public void lateWallpaperCaptureStillContractsFromExpandedSize() throws Exception {
        Object state = state();
        call(state, "onContentFinished");
        call(state, "beginClose");
        assertEquals(1f, sample(state, Float.NaN), 0f);
        assertEquals(.7f, sample(state, .7f), 0f);
        assertEquals(0f, sample(state, 0f), 0f);
    }
    @Test public void disabledNativeAnimationsCanReachThePanelWithoutASpringSample() throws Exception {
        Object state = state();
        call(state, "onContentFinished");
        assertEquals(1f, sample(state, Float.NaN), 0f);
    }
    private static Object state() throws Exception {
        try { return Class.forName("com.hellovoid.liquiddock.OpenFolderMotionState").getDeclaredConstructor().newInstance(); }
        catch (ClassNotFoundException e) { throw new AssertionError("Missing native folder spring continuity policy", e); }
    }
    private static void call(Object state, String method) throws Exception {
        state.getClass().getDeclaredMethod(method).invoke(state);
    }
    private static float sample(Object state, float progress) throws Exception {
        return (Float) state.getClass().getDeclaredMethod("sample", float.class).invoke(state, progress);
    }
}
