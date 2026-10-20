package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WorkstationTaskbarGeometryTest {
    @Test public void scaledPortraitIconsMoveDownToTheActualTaskbarCenter() {
        float actualCenter = 2865f + 115f * .44059408f;
        float target = 3008f - 105f / 2f;
        assertEquals(39.8315f, WorkstationTaskbarGeometry.centeredIconRowTranslation(
                0f, target, actualCenter, 1f, 0f), .001f);
    }

    @Test public void centeringDoesNotAccumulateAndKeepsTheUserOffset() {
        assertEquals(11f, WorkstationTaskbarGeometry.centeredIconRowTranslation(
                0f, 1798f, 1787f, 1f, 0f), .001f);
        assertEquals(11f, WorkstationTaskbarGeometry.centeredIconRowTranslation(
                11f, 1798f, 1798f, 1f, 0f), .001f);
        assertEquals(16f, WorkstationTaskbarGeometry.centeredIconRowTranslation(
                11f, 1798f, 1798f, 1f, 5f), .001f);
        assertEquals(16f, WorkstationTaskbarGeometry.centeredIconRowTranslation(
                16f, 1798f, 1803f, 1f, 5f), .001f);
    }

    @Test public void screenCorrectionIsConvertedToTheRowsParentScale() {
        assertEquals(20f, WorkstationTaskbarGeometry.centeredIconRowTranslation(
                0f, 100f, 90f, .5f, 0f), .001f);
        assertEquals(20f, WorkstationTaskbarGeometry.centeredIconRowTranslation(
                20f, 100f, 100f, .5f, 0f), .001f);
    }

    @Test public void oddHeightBarRetainsItsFractionalCenterOnFollowingFrames() {
        float current = WorkstationTaskbarGeometry.centeredIconRowTranslation(
                0f, 100.5f, 100f, 1f, 0f);
        assertEquals(.5f, current, 0f);
        for (int frame = 0; frame < 10; frame++) {
            current = WorkstationTaskbarGeometry.centeredIconRowTranslation(
                    current, 100.5f, 100f + current, 1f, 0f);
            assertEquals(.5f, current, 0f);
        }
    }
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
        assertEquals(950f, WorkstationTaskbarGeometry.visibleTop(1080, 130), 0.001f);
    }

    @Test public void taskbarBodyUsesAbsoluteScreenCoordinates() {
        assertEquals(0f, WorkstationTaskbarGeometry.bodyUvLeft(80, -80, 0, 2200), 0.001f);
        assertEquals(0f, WorkstationTaskbarGeometry.bodyUvBottom(80, 1180, 1260, 500), 0.001f);
    }
}
