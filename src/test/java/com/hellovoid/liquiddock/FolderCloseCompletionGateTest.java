package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class FolderCloseCompletionGateTest {
    @Test public void contentFirstMustWaitForBackdropSpring() {
        FolderCloseCompletionGate gate = new FolderCloseCompletionGate();
        gate.begin(5, true);
        assertFalse(gate.contentFinished(5));
        assertTrue(gate.isActive());
        assertTrue(gate.backdropFinished(5));
        assertFalse(gate.isActive());
    }

    @Test public void backdropFirstMustWaitForContent() {
        FolderCloseCompletionGate gate = new FolderCloseCompletionGate();
        gate.begin(8, true);
        assertFalse(gate.backdropFinished(8));
        assertTrue(gate.contentFinished(8));
    }

    @Test public void nonAnimatedCloseReleasesWithContent() {
        FolderCloseCompletionGate gate = new FolderCloseCompletionGate();
        gate.begin(12, false);
        assertTrue(gate.contentFinished(12));
    }

    @Test public void staleCallbackCannotFinishNewFolder() {
        FolderCloseCompletionGate gate = new FolderCloseCompletionGate();
        gate.begin(13, true);
        gate.begin(14, true);
        assertFalse(gate.contentFinished(13));
        assertFalse(gate.backdropFinished(13));
        assertFalse(gate.contentFinished(14));
        assertTrue(gate.backdropFinished(14));
    }

    @Test public void cancelledCloseCannotRestoreNativeMaterial() {
        FolderCloseCompletionGate gate = new FolderCloseCompletionGate();
        gate.begin(1, true);
        gate.cancel();
        assertFalse(gate.contentFinished(1));
        assertFalse(gate.backdropFinished(1));
    }
}
