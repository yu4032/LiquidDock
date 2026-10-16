package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class FolderDesktopCaptureGateTest {
    @Test public void preOpenFrameStaysValidAfterSealing() {
        FolderDesktopCaptureGate gate = new FolderDesktopCaptureGate();
        int[] count = {0};
        assertTrue(gate.mayPrepare());
        assertTrue(gate.commit(() -> count[0]++));
        assertTrue(gate.seal());
        assertFalse(gate.mayPrepare());
        assertFalse(gate.commit(() -> count[0]++));
        assertEquals(1, count[0]);
    }

    @Test public void cannotPublishLateGpuFrameDuringNativeOpening() {
        FolderDesktopCaptureGate gate = new FolderDesktopCaptureGate();
        assertTrue(gate.mayPrepare());
        assertFalse(gate.seal());
        assertFalse(gate.commit(() -> fail("must not publish stale frame")));
        assertFalse(gate.mayPrepare());
    }

    @Test public void programmaticOpenWithoutPrewarmFallsBackToNative() {
        FolderDesktopCaptureGate gate = new FolderDesktopCaptureGate();
        assertFalse(gate.seal());
        assertFalse(gate.commit(() -> fail("late frame")));
    }
}
