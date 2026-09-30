package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts that keep Dock geometry redraws from starving PassBlur OES delivery. */
public class Miuix307RenderSchedulingContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java");

    @Test
    public void dockDrawRequestsUseOneLatestStateQueueSlot() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains(
                "private final AtomicBoolean renderScheduled = new AtomicBoolean(false);"));
        assertTrue(source.contains(
                "private final AtomicBoolean renderRequested = new AtomicBoolean(false);"));
        assertTrue(source.contains(
                "private final AtomicBoolean producerRenderRequested = new AtomicBoolean(false);"));
        assertTrue(source.contains("private void requestRender(boolean fromFrameCallback)"));
        assertTrue(source.contains("renderScheduled.compareAndSet(false, true)"));
        assertTrue(source.contains("renderHandler.post(this::runScheduledRender);"));
        assertTrue(source.contains("drawLatestFrame(fromFrameCallback);"));

        assertFalse("UI callers must not enqueue an unbounded full-draw runnable per event",
                source.contains("renderHandler.post(() -> drawLatestFrame(false))"));
        assertFalse("OES callback must join the same coalesced queue instead of drawing inline",
                source.contains("drawLatestFrame(true);"));
    }

    @Test
    public void requestsArrivingDuringDrawScheduleOneLooperTailFollowUp() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("renderRequested.set(false);"));
        assertTrue(source.contains("renderScheduled.set(false);"));
        assertTrue(source.contains(
                "if (renderRequested.get() && renderScheduled.compareAndSet(false, true))"));
        assertTrue("follow-up render must return to the Handler queue between GL passes",
                source.contains("renderHandler.post(this::runScheduledRender);"));
    }
}
