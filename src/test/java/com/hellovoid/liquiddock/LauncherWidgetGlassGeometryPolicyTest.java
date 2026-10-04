package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LauncherWidgetGlassGeometryPolicyTest {
    @Test
    public void recognizesLauncher450WidgetOccupancyOwners() {
        assertTrue(LauncherWidgetGlassGeometryPolicy.isHorizontalSpanOwnerClassName(
                "com.miui.home.launcher.LauncherWidgetView"));
        assertTrue(LauncherWidgetGlassGeometryPolicy.isHorizontalSpanOwnerClassName(
                "com.miui.home.launcher.maml.MaMlWidgetView"));
        assertFalse(LauncherWidgetGlassGeometryPolicy.isHorizontalSpanOwnerClassName(
                "com.miui.home.launcher.LauncherAppWidgetHostView"));
    }

    @Test
    public void zeroDeltaLeavesCenteredStockWidthUntouched() {
        assertTrue(LauncherWidgetGlassGeometryPolicy.horizontalWidthDelta(
                2, 595, 595) == 0);
    }

    @Test
    public void oneByOneNeverChangesFromOuterFrameDelta() {
        assertTrue(LauncherWidgetGlassGeometryPolicy.horizontalWidthDelta(
                1, 260, 331) == 0);
    }

    @Test
    public void zeroOffsetBaselineProducesNoChange() {
        assertTrue(LauncherWidgetGlassGeometryPolicy.horizontalWidthDelta(
                2, 595, 595) == 0);
    }

    @Test
    public void widerTwoColumnFrameAddsOnlyItsDelta() {
        assertTrue(LauncherWidgetGlassGeometryPolicy.horizontalWidthDelta(
                2, 609, 595) == 14);
        assertTrue(LauncherWidgetGlassGeometryPolicy.horizontalWidthDelta(
                2, 714, 595) == 119);
    }

    @Test
    public void narrowerTwoColumnFrameShrinksSymmetricallyByItsDelta() {
        assertTrue(LauncherWidgetGlassGeometryPolicy.horizontalWidthDelta(
                2, 560, 595) == -35);
    }

    @Test
    public void invalidFramesFailClosedToNoChange() {
        assertTrue(LauncherWidgetGlassGeometryPolicy.horizontalWidthDelta(
                2, 0, 595) == 0);
        assertTrue(LauncherWidgetGlassGeometryPolicy.horizontalWidthDelta(
                2, 595, 0) == 0);
    }
}
