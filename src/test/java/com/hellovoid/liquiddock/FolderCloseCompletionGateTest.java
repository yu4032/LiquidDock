package com.hellovoid.liquiddock;

import java.lang.reflect.Method;
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

    @Test public void nativeCompletionMustWaitForVisualFade() throws Exception {
        FolderCloseCompletionGate gate = new FolderCloseCompletionGate();
        beginWithFade(gate, 21, false, true);

        assertFalse(gate.contentFinished(21));
        assertTrue(gate.isActive());
        assertTrue(visualFadeFinished(gate, 21));
        assertFalse(gate.isActive());
    }

    @Test public void visualFadeMustWaitForNativeCompletion() throws Exception {
        FolderCloseCompletionGate gate = new FolderCloseCompletionGate();
        beginWithFade(gate, 22, true, true);

        assertFalse(visualFadeFinished(gate, 22));
        assertTrue(gate.isActive());
        assertFalse(gate.backdropFinished(22));
        assertTrue(gate.contentFinished(22));
        assertFalse(gate.isActive());
    }

    @Test public void staleVisualFadeCannotFinishNewSession() throws Exception {
        FolderCloseCompletionGate gate = new FolderCloseCompletionGate();
        beginWithFade(gate, 23, false, true);
        beginWithFade(gate, 24, false, true);

        assertFalse(visualFadeFinished(gate, 23));
        assertFalse(gate.contentFinished(24));
        assertTrue(visualFadeFinished(gate, 24));
    }

    private static void beginWithFade(FolderCloseCompletionGate gate, long serial,
            boolean hasBackdropSpring, boolean hasVisualFade) throws Exception {
        Method begin = gate.getClass().getDeclaredMethod("begin", long.class,
                boolean.class, boolean.class);
        begin.invoke(gate, serial, hasBackdropSpring, hasVisualFade);
    }

    private static boolean visualFadeFinished(FolderCloseCompletionGate gate, long serial)
            throws Exception {
        Method finished = gate.getClass().getDeclaredMethod("visualFadeFinished", long.class);
        return (Boolean) finished.invoke(gate, serial);
    }
}
