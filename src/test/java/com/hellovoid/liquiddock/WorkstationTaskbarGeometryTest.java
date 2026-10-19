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

    @Test public void taskbarBodyUsesAbsoluteScreenCoordinates() {
        assertEquals(0f, WorkstationTaskbarGeometry.bodyUvLeft(80, -80, 0, 2200), 0.001f);
        assertEquals(0f, WorkstationTaskbarGeometry.bodyUvBottom(80, 1180, 1260, 500), 0.001f);
    }
}
