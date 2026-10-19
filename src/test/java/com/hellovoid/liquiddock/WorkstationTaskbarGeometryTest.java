package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class WorkstationTaskbarGeometryTest {
    @Test public void outputIncludesEntireScreenAndBleed() {
        int bleed = WorkstationTaskbarGeometry.bleedPx(2f);
        assertEquals(80, bleed);
        assertEquals(580, WorkstationTaskbarGeometry.leftOutputMargin(500, bleed, 10));
        assertEquals(580, WorkstationTaskbarGeometry.rightOutputMargin(500, 1000, 2000, bleed, 10));
        assertEquals(180, WorkstationTaskbarGeometry.bottomOutputMargin(850, 150, 1100, bleed, 10));
    }

    @Test public void dockMovesToPhysicalBottomWithoutAccumulatingDelta() {
        assertEquals(100f, WorkstationTaskbarGeometry.bottomTranslation(1100, 1000, 0f), 0.001f);
        assertEquals(100f, WorkstationTaskbarGeometry.bottomTranslation(1100, 1100, 100f), 0.001f);
    }

    @Test public void visibleHeightIsNativeHeightEvenWhenOpticsBleedPastScreen() {
        assertEquals(960f, WorkstationTaskbarGeometry.visibleTop(1080, 120), 0.001f);
        assertEquals(1020f, WorkstationTaskbarGeometry.visibleCenter(1080, 120), 0.001f);
        // A 40px extruded bottom edge affects sampling, never the visible taskbar center.
        assertEquals(1020f, WorkstationTaskbarGeometry.visibleCenter(1080, 120), 0.001f);
        assertEquals(950f, WorkstationTaskbarGeometry.visibleTop(1080, 130), 0.001f);
    }

    @Test public void iconRowCorrectionIsGeometricAndStable() {
        assertEquals(21f, WorkstationTaskbarGeometry.iconCenterCorrection(1020f, 999f), 0.001f);
        assertEquals(0f, WorkstationTaskbarGeometry.iconCenterCorrection(1020f, 1020f), 0.001f);
        assertEquals(-7f, WorkstationTaskbarGeometry.iconCenterCorrection(1020f, 1027f), 0.001f);
    }

    @Test public void taskbarBodyUsesAbsoluteScreenCoordinates() {
        assertEquals(0f, WorkstationTaskbarGeometry.bodyUvLeft(80, -80, 0, 2200), 0.001f);
        assertEquals(0f, WorkstationTaskbarGeometry.bodyUvBottom(80, 1180, 1260, 500), 0.001f);
    }
}
