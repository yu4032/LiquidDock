package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contract for keeping Gboard floating-glass drag work bounded per frame. */
public class GboardFloatingDragPerformanceContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(String name) throws Exception {
        return Files.readString(MAIN.resolve(name));
    }

    private static String methodSlice(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        return from >= 0 && to > from ? source.substring(from, to) : "";
    }

    @Test public void coordinatorCoalescesGeometryToVsyncInsteadOfCapturingInPredraw() throws Exception {
        String coordinator = read("GboardFloatingGlassCoordinator.java");
        assertTrue(coordinator.contains("GboardFloatingCoalescingGate geometryGate"));
        assertTrue(coordinator.contains("requestGeometryFrame(state)"));
        assertTrue(coordinator.contains("runGeometryFrame"));
        assertTrue(coordinator.contains("postOnAnimation"));
        assertTrue(coordinator.contains("geometryGate.request()"));
        assertTrue(coordinator.contains("geometryGate.begin()"));
        assertTrue(coordinator.contains("geometryGate.complete()"));
        assertFalse(coordinator.contains("preDrawListener = () -> {\n            syncGeometry(state);"));
        assertFalse(coordinator.contains("postDelayed"));
    }

    @Test public void positionOnlyGeometryDoesNotReconcileProducerOrQueueEveryEvent() throws Exception {
        String session = read("GboardFloatingGlassSession.java");
        String update = methodSlice(session, "void updateGeometry(", "void attachOutput(");
        assertTrue(session.contains("GboardFloatingCoalescingGate renderGate"));
        assertTrue(update.contains("requestRender()"));
        assertFalse(update.contains("reconcileRoot()"));
        assertFalse(update.contains("postToRenderThread(this::renderCurrent)"));
    }

    @Test public void freshFramesAndGeometryShareLatestStateRenderGate() throws Exception {
        String session = read("GboardFloatingGlassSession.java");
        String fresh = methodSlice(session, "public void onFreshFrame(", "@Override\n    public void onTerminalFailure");
        assertTrue(session.contains("drainRender"));
        assertTrue(session.contains("requestRenderFromRenderThread"));
        assertTrue(fresh.contains("requestRenderFromRenderThread()"));
        assertFalse(fresh.contains("renderCurrent();"));
    }
}
