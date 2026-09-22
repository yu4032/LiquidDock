package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DockInstallConfigTest {
    @Test
    public void dpNormalizationIsPureAndSeparatesDimensionAndCornerUnits() {
        DockInstallConfig config = DockInstallConfig.normalize(
                true, true,
                10, -4, 72,
                3, 5, 6,
                true, false,
                2.5f);

        assertTrue(config.enabled);
        assertTrue(config.squircle);
        assertEquals(25, config.widthOffsetPx);
        assertEquals(-10, config.heightOffsetPx);
        assertEquals(72, config.blurRadius);
        assertEquals(3, config.cornerOffsetPx);
        assertEquals(5, config.blurCornerOffsetPx);
        assertEquals(15, config.spacingPx);
    }

    @Test
    public void rawPixelModeDoesNotApplyDensity() {
        DockInstallConfig config = DockInstallConfig.normalize(
                false, false,
                11, 12, 100,
                13, 14, 15,
                false, false,
                3f);

        assertFalse(config.enabled);
        assertEquals(11, config.widthOffsetPx);
        assertEquals(12, config.heightOffsetPx);
        assertEquals(13, config.cornerOffsetPx);
        assertEquals(14, config.blurCornerOffsetPx);
        assertEquals(15, config.spacingPx);
    }
}
