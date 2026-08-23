package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Device regression: keep main's persistent Dock PassBlur binding; never add a per-vsync transaction pump. */
public class DockRealtimeProducerContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void dockUsesMainContinuousOnBindProducerMode() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        int bindStart = bridge.indexOf("static Binding bind(View materialHost, Surface producerSurface");
        int nextMethod = bridge.indexOf("static void requestSingleUpdate(", bindStart);
        assertTrue(bindStart >= 0 && nextMethod > bindStart);
        String bind = bridge.substring(bindStart, nextMethod);
        assertTrue(bind.contains("setUpdateTextureFlag.invoke("));
        assertTrue(bind.contains("Boolean.TRUE"));
        assertTrue(bridge.contains("mode=continuous-on-bind"));
        assertTrue(view.contains("input.setOnFrameAvailableListener"));
        assertTrue(view.contains("drawLatestFrame(true);"));
    }

    @Test public void dockMustNotSubmitSurfaceControlTransactionsEveryVsync() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertFalse(bridge.contains("static void requestFrame(Binding binding)"));
        assertFalse(view.contains("producerPump"));
        assertFalse(view.contains("postOnAnimation(producerPump)"));
        assertFalse(view.contains("producerRequestCount"));
        // Passive OES/draw counters are allowed; they observe cadence without scheduling frames.
    }

    @Test public void runtimeShutdownUnbindsPersistentDockProducer() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(view.contains("Miuix307PassBlurBridge.unbind(currentBinding)"));
        assertTrue(view.contains("renderThread.quitSafely()"));
        assertTrue(bridge.contains("Boolean.FALSE"));
        assertTrue(bridge.contains("setPassBlurSurface.invoke(transaction, binding.rootSurface, null)"));
        assertFalse("shutdown must not require CPU pixel readback", view.contains("glReadPixels"));
    }
}
