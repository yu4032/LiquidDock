package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DockBackdropWorldLockStateTest {
    @Test
    public void samePositionUsesCenteredOverscanCrop() {
        DockBackdropWorldLockState.Crop crop = DockBackdropWorldLockState.compute(
                1000, 200, 1120, 260,
                60, 30,
                100, 500,
                100, 500);

        assertEquals(60, crop.sourceLeft);
        assertEquals(30, crop.sourceTop);
        assertEquals(1.12f, crop.scaleX, 0.0001f);
        assertEquals(1.30f, crop.scaleY, 0.0001f);
    }

    @Test
    public void movingDockRightReadsFurtherRightFromPresentedWorldBuffer() {
        DockBackdropWorldLockState.Crop crop = DockBackdropWorldLockState.compute(
                1000, 200, 1120, 260,
                60, 30,
                100, 500,
                128, 500);

        assertEquals(88, crop.sourceLeft);
        assertEquals(30, crop.sourceTop);
        assertEquals(-88f, crop.translateX, 0.0001f);
        assertEquals(-30f, crop.translateY, 0.0001f);
    }

    @Test
    public void cropClampsToOverscanInsteadOfExposingTransparentPixels() {
        DockBackdropWorldLockState.Crop crop = DockBackdropWorldLockState.compute(
                1000, 200, 1120, 260,
                60, 30,
                100, 500,
                300, 420);

        assertEquals(120, crop.sourceLeft);
        assertEquals(0, crop.sourceTop);
        assertEquals(-120f, crop.translateX, 0.0001f);
        assertEquals(0f, crop.translateY, 0.0001f);
    }
}
