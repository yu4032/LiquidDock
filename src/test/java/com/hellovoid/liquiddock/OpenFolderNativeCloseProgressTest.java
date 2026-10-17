package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class OpenFolderNativeCloseProgressTest {
    @Test public void stationaryTranslationDoesNotFreezeTheScaleSpring() {
        OpenFolderNativeCloseProgress p = new OpenFolderNativeCloseProgress();
        p.capture(100f, 0f, 3f, 100f, 200f, 3f);
        assertEquals("x=true y=false scale=true", p.channelDescription());
        assertEquals(1f, p.sample(100f, 0f, 3f, 100f, 200f, 3f), 0f);
        assertEquals(.5f, p.sample(100f, 0f, 2f, 100f, 200f, 3f), 0f);
        assertEquals(0f, p.sample(100f, 0f, 1f, 100f, 200f, 3f), 0f);
    }

    @Test public void stationaryScaleDoesNotForceEarlyCollapse() {
        OpenFolderNativeCloseProgress p = new OpenFolderNativeCloseProgress();
        p.capture(0f, 200f, 1f, 100f, 200f, 3f);
        assertEquals("x=false y=true scale=false", p.channelDescription());
        assertEquals(.75f, p.sample(0f, 150f, 1f, 100f, 200f, 3f), .001f);
        assertEquals(0f, p.sample(0f, 0f, 1f, 100f, 200f, 3f), 0f);
    }

    @Test public void inactiveChannelsDoNotInventProgress() {
        OpenFolderNativeCloseProgress p = new OpenFolderNativeCloseProgress();
        p.capture(0f, 0f, 1f, 0f, 0f, 1f);
        assertFalse(p.hasChannels());
        assertTrue(Float.isNaN(p.sample(0f, 0f, 1f, 0f, 0f, 1f)));
        p.reset();
        p.capture(100f, 0f, 1f, 100f, 0f, 1f);
        assertEquals(0f, p.sample(0f, 0f, 1f, 100f, 0f, 1f), 0f);
    }

    @Test public void independentFolmeChannelsCanContractAtDifferentRates() {
        OpenFolderNativeCloseProgress p = new OpenFolderNativeCloseProgress();
        p.capture(200f, 150f, 3f, 200f, 150f, 3f);
        assertEquals(.4f, p.sample(160f, 60f, 2f, 200f, 150f, 3f), .001f);
    }
    @Test public void roundedSmallFolderUsesAnimatedParentPlateWhenChildChannelsAreStatic() {
        OpenFolderNativeCloseProgress p = new OpenFolderNativeCloseProgress();
        p.capture(0f, 0f, 1f, 0f, 0f, Float.NaN);
        assertFalse("rounded child exposes no Folme scale", p.hasChannels());
        assertTrue(Float.isNaN(p.sample(0f, 0f, 1f, 0f, 0f, Float.NaN)));

        OpenFolderGlassGeometry.Frame icon =
                new OpenFolderGlassGeometry.Frame(250, 250, 330, 330, 17);
        OpenFolderGlassGeometry.Frame expanded =
                new OpenFolderGlassGeometry.Frame(100, 100, 500, 500, 65);
        OpenFolderGlassGeometry.Frame half =
                new OpenFolderGlassGeometry.Frame(175, 175, 415, 415, 40);
        assertTrue(Float.isNaN(OpenFolderNativeCloseProgress.sampleVisualFrame(
                icon, expanded, expanded)));
        assertEquals(.5f, OpenFolderNativeCloseProgress.sampleVisualFrame(
                icon, expanded, half), .001f);
        assertEquals(0f, OpenFolderNativeCloseProgress.sampleVisualFrame(
                icon, expanded, icon), .001f);
    }

    @Test public void translationOnlyPlateIsAValidProgressSignal() {
        OpenFolderGlassGeometry.Frame icon =
                new OpenFolderGlassGeometry.Frame(500, 200, 600, 300, 20);
        OpenFolderGlassGeometry.Frame start =
                new OpenFolderGlassGeometry.Frame(200, 200, 300, 300, 20);
        OpenFolderGlassGeometry.Frame mid =
                new OpenFolderGlassGeometry.Frame(350, 200, 450, 300, 20);
        assertEquals(.5f, OpenFolderNativeCloseProgress.sampleVisualFrame(
                icon, start, mid), .001f);
    }

    @Test public void staleAndUnrelatedVisualBoundsCannotFabricateAClosingSpring() {
        OpenFolderGlassGeometry.Frame icon =
                new OpenFolderGlassGeometry.Frame(300, 300, 400, 400, 22);
        OpenFolderGlassGeometry.Frame almostIcon =
                new OpenFolderGlassGeometry.Frame(295, 295, 405, 405, 25);
        assertTrue(Float.isNaN(OpenFolderNativeCloseProgress.sampleVisualFrame(
                icon, almostIcon, almostIcon)));
        OpenFolderGlassGeometry.Frame expanded =
                new OpenFolderGlassGeometry.Frame(100, 100, 600, 600, 70);
        OpenFolderGlassGeometry.Frame growing =
                new OpenFolderGlassGeometry.Frame(40, 40, 660, 660, 70);
        assertTrue(Float.isNaN(OpenFolderNativeCloseProgress.sampleVisualFrame(
                icon, expanded, growing)));
        assertTrue(Float.isNaN(OpenFolderNativeCloseProgress.sampleVisualFrame(
                icon, null, growing)));
    }

}
