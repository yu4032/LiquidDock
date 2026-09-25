package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Locks the PassBlur consumer to latest-frame scheduling without changing native producer policy. */
public class DockPassBlurFrameSchedulingContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java");

    @Test
    public void producerSignalsUseIndependentLooperAndLatestRenderGate() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("LiquidDock-PassBlur-FrameSignal"));
        assertTrue(source.contains("setOnFrameAvailableListener(texture ->"));
        assertTrue(source.contains("}, frameSignalHandler);"));
        assertTrue(source.contains("requestLatestRender(true);"));
        assertTrue(source.contains("renderScheduled.compareAndSet(false, true)"));
        assertTrue(source.contains("producerRenderDirty.getAndSet(false)"));
        assertTrue(source.contains("frameSignalThread.quitSafely();"));

        assertFalse("producer callback must not execute the GL pipeline inline",
                source.contains("frameAvailable.set(true);\n            drawLatestFrame(true);"));
    }

    @Test
    public void ordinarySceneRefreshesDoNotBypassTheGate() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains(
                "if (producerRecovery.hasFreshFrame()) requestLatestRender(false);"));
        assertFalse("scene/config refreshes must not enqueue duplicate full renders",
                source.contains(
                        "if (producerRecovery.hasFreshFrame()) renderHandler.post(() -> drawLatestFrame(false));"));
    }
}
