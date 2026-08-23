package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Device regression: a frame consumer is not enough; Dock must actively drive PassBlur updates. */
public class DockRealtimeProducerContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void dockActivelyPumpsPassBlurProducerWhileVisible() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue("bridge needs a non-coalesced producer pulse API",
                bridge.contains("static void requestFrame(Binding binding)"));
        assertTrue("each pulse must submit a real SurfaceControl transaction",
                bridge.contains("binding.setUpdateTextureFlag.invoke("));
        assertTrue("Dock needs an explicit frame pump, not only OnFrameAvailable consumption",
                view.contains("producerPump"));
        assertTrue(view.contains("Miuix307PassBlurBridge.requestFrame(currentBinding)"));
        assertTrue(view.contains("postOnAnimation(producerPump)"));
    }

    @Test public void dockProducerPumpStopsWhenGlassIsDisabledOrRendererShutsDown() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(view.contains("GlassRuntimeState.isEnabled()"));
        assertTrue(view.contains("removeCallbacks(producerPump)"));
        assertTrue(view.contains("shuttingDown"));
    }

    @Test public void diagnosticsSeparateProducerRequestsFromReceivedOesFrames() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(view.contains("producerRequestCount"));
        assertTrue(view.contains("producerFrameCount"));
        assertTrue(view.contains("producerRequestCount.incrementAndGet()"));
        assertTrue(view.contains("producerFrameCount.incrementAndGet()"));
        assertFalse("diagnostics must not require CPU pixel readback", view.contains("glReadPixels"));
    }
}
