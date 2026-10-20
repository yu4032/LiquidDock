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
        assertEquals(950f, WorkstationTaskbarGeometry.visibleTop(1080, 130), 0.001f);
    }

    @Test public void iconTranslationUsesOnlyVendorBaselineAndConfiguredOffset() {
        assertEquals(12f, WorkstationTaskbarGeometry.iconRowTranslation(0f, 12f), 0.001f);
        assertEquals(-8f, WorkstationTaskbarGeometry.iconRowTranslation(0f, -8f), 0.001f);
        assertEquals(20f, WorkstationTaskbarGeometry.iconRowTranslation(8f, 12f), 0.001f);
        // Repeating the same request must not treat our previous translation as the new baseline.
        assertEquals(20f, WorkstationTaskbarGeometry.iconRowTranslation(8f, 12f), 0.001f);
        // Editing the slider replaces, rather than accumulates, the former offset.
        assertEquals(14f, WorkstationTaskbarGeometry.iconRowTranslation(8f, 6f), 0.001f);
    }

    @Test public void iconTranslationIsIndependentOfScreenOrientation() {
        float nativeY = 4f;
        float offset = 10f;
        float beforeRotation = WorkstationTaskbarGeometry.iconRowTranslation(nativeY, offset);
        float afterRotation = WorkstationTaskbarGeometry.iconRowTranslation(nativeY, offset);
        assertEquals(14f, beforeRotation, 0.001f);
        assertEquals(beforeRotation, afterRotation, 0.001f);
    }

    @Test public void taskbarBodyUsesAbsoluteScreenCoordinates() {
        assertEquals(0f, WorkstationTaskbarGeometry.bodyUvLeft(80, -80, 0, 2200), 0.001f);
        assertEquals(0f, WorkstationTaskbarGeometry.bodyUvBottom(80, 1180, 1260, 500), 0.001f);
    }
}
