package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import org.junit.Test;

public class LauncherWallpaperTransformStateTest {
    @Test
    public void captureUsesTransformAtProducerTimeRatherThanCallbackTime() {
        LauncherWallpaperTransformState state = new LauncherWallpaperTransformState();
        state.update(100, 1f, .5f, .5f);
        state.update(200, 1.14f, .5f, .5f);
        assertEquals(1f, state.at(150).scale, 0f);
        assertEquals(1.14f, state.latest().scale, 0f);
        assertNull(state.at(99));
    }

    @Test
    public void staleOrInvalidNativeSamplesCannotRewindTheAnimation() {
        LauncherWallpaperTransformState state = new LauncherWallpaperTransformState();
        assertTrue(state.update(200, 1.08f, .5f, .4f));
        assertFalse(state.update(199, 1.14f, .5f, .5f));
        assertFalse(state.update(300, Float.NaN, .5f, .5f));
        assertEquals(1.08f, state.latest().scale, 0f);
        assertEquals(.4f, state.latest().pivotY, 0f);
    }

    @Test
    public void samplingRemainsAlignedAcrossBackdropReplacementAndPivotChanges() {
        LauncherWallpaperTransformState.Sample captured =
                new LauncherWallpaperTransformState.Sample(1, 1f, .5f, .5f);
        LauncherWallpaperTransformState.Sample now =
                new LauncherWallpaperTransformState.Sample(2, 1.14f, .3f, .4f);
        LauncherWallpaperTransformState.Sample later =
                new LauncherWallpaperTransformState.Sample(3, 1.06f, .6f, .5f);
        float[] mapping = new float[4];
        float originalX = .22f, originalY = .73f;
        float screenX = now.pivotX + now.scale * (originalX - now.pivotX);
        float screenY = now.pivotY + now.scale * (originalY - now.pivotY);
        LauncherWallpaperTransformState.map(captured, now, mapping);
        assertEquals(originalX, screenX * mapping[0] + mapping[2], 1e-6f);
        assertEquals(originalY, screenY * mapping[1] + mapping[3], 1e-6f);
        float capturedX = screenX, capturedY = screenY;
        screenX = later.pivotX + later.scale * (originalX - later.pivotX);
        screenY = later.pivotY + later.scale * (originalY - later.pivotY);
        LauncherWallpaperTransformState.map(now, later, mapping);
        assertEquals(capturedX, screenX * mapping[0] + mapping[2], 1e-6f);
        assertEquals(capturedY, screenY * mapping[1] + mapping[3], 1e-6f);
    }

    @Test
    public void historyIsBoundedAndDoesNotGuessAnEvictedCaptureTransform() {
        LauncherWallpaperTransformState state = new LauncherWallpaperTransformState();
        for (int i = 1; i <= 600; i++) state.update(i, 1 + i * .0001f, .5f, .5f);
        assertNull(state.at(1));
        assertEquals(1.06f, state.at(600).scale, 1e-6f);
    }
}
