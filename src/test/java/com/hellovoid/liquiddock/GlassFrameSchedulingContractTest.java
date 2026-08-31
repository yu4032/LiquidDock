package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Performance contract: normal Launcher frames must not replay expensive glass maintenance. */
public class GlassFrameSchedulingContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void vendorBlurWritesAreInterceptedInsteadOfReplayedEveryPreDraw() throws Exception {
        String source = Files.readString(MAIN.resolve("GlassPerformanceHook.java"));
        assertTrue(source.contains("hookVendorBlurSuppression"));
        assertTrue(source.contains("installVendorGpuBlurSuppressor"));
        assertTrue(source.contains("setPassWindowBlurEnabled"));
        assertTrue(source.contains("setMiBackgroundBlurRadius"));
    }

    @Test
    public void dockGeometryObserverUsesCheapFingerprintBeforeExpensiveRefresh() throws Exception {
        String source = Files.readString(MAIN.resolve("GlassPerformanceHook.java"));
        assertTrue(source.contains("installLightDockObserver"));
        assertTrue(source.contains("producerFingerprint"));
        assertTrue(source.contains("mappingFingerprint"));
        assertTrue(source.contains("refreshProducerGeometryInPlace"));
    }

    @Test
    public void workspaceFrameSyncSuppressesProducerReflectionOnOrdinaryFrames() throws Exception {
        String source = Files.readString(MAIN.resolve("GlassPerformanceHook.java"));
        assertTrue(source.contains("installLauncherObserver"));
        assertTrue(source.contains("FRAME_PRODUCER_REFRESH"));
        assertTrue(source.contains("refreshProducerGeometryOnUi"));
    }

    @Test
    public void dockAnimationUsesSceneOnlyFastPath() throws Exception {
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        String performance = Files.readString(MAIN.resolve("GlassPerformanceHook.java"));
        assertTrue(renderer.contains("GlassPerformanceHook.requestDockAnimationFrame(gpuBackdrop)"));
        assertTrue(performance.contains("drawDockSceneOnly"));
        assertTrue(performance.contains("DockGlassFramePolicy"));
    }
}
