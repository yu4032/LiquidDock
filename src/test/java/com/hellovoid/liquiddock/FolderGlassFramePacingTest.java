package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class FolderGlassFramePacingTest {
    @Test public void firstFrameAndNewEndpointsAreNeverDeferred() {
        FolderGlassFramePacing pacing = new FolderGlassFramePacing();
        assertTrue(pacing.allow(100, .6f));
        assertTrue(pacing.allow(101, 1f));
        assertFalse(pacing.allow(102, .9f));
        assertTrue(pacing.allow(103, 0f));
    }

    @Test public void expensiveGlassFramesAreCoalescedAtHighNativeRefreshRates() {
        FolderGlassFramePacing pacing = new FolderGlassFramePacing();
        assertTrue(pacing.allow(0, .8f));
        assertFalse(pacing.allow(6_060_000L, .75f));
        assertTrue(pacing.allow(12_120_000L, .7f));
        assertFalse(pacing.allow(18_180_000L, .65f));
        assertTrue(pacing.allow(24_240_000L, .6f));
    }

    @Test public void stableEndpointCannotForceUnlimitedDuplicateFrames() {
        FolderGlassFramePacing pacing = new FolderGlassFramePacing();
        assertTrue(pacing.allow(0, 1f));
        assertFalse(pacing.allow(1, 1f));
        assertTrue(pacing.allow(FolderGlassFramePacing.MIN_INTERVAL_NANOS, 1f));
    }

    @Test public void invalidSamplesCannotQueueWork() {
        FolderGlassFramePacing pacing = new FolderGlassFramePacing();
        assertFalse(pacing.allow(0, Float.NaN));
        assertFalse(pacing.allow(0, Float.POSITIVE_INFINITY));
        assertTrue(pacing.allow(0, .4f));
    }
}
