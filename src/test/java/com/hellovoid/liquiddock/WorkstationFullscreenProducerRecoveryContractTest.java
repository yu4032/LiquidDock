package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression contract for workstation Dock recovery after Launcher loses its PassBlur producer. */
public class WorkstationFullscreenProducerRecoveryContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void workstationLauncherResumeForcesProducerRebindWithoutRebuildingGlass() throws Exception {
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));

        assertTrue(pipeline.contains("installWorkstationResumeProducerRecovery(classLoader);"));
        assertTrue(pipeline.contains("private static void installWorkstationResumeProducerRecovery("));
        assertTrue(pipeline.contains("launcherClass.getDeclaredMethod(\"onResume\")"));
        assertTrue(pipeline.contains("if (MainHook.isWorkstationMode())"));
        assertTrue(pipeline.contains(
                "Miuix307ZeroCopyRenderer.rebindProducer(\"workstation-launcher-resume\")"));

        assertFalse(pipeline.contains(
                "MiuixGlassHook.invalidateBinding(background); // workstation-launcher-resume"));
    }

    @Test public void producerRebindDropsStaleFrameworkBindingAndWaitsForANewOesFrame() throws Exception {
 String r=Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java")),v=Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java")); assertTrue(r.contains("gpuBackdrop.replaceProducerGeneration(reason)")); assertTrue(v.contains("void replaceProducerGeneration(String reason)")&&v.contains("Miuix307PassBlurBridge.unbind(stale)")&&v.contains("hasConsumedFrame = false")&&v.contains("frameAvailable.set(false)"));
    }

    @Test public void producerRebindNeverReusesAnAlreadyParceledBufferQueueProducer() throws Exception {
 String v=Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java")); assertTrue(v.contains("recreateInputProducer")&&v.contains("releaseInputProducer(staleProducer, staleInput)")&&v.contains("producer.release()")&&v.contains("input.release()")&&v.contains("glDeleteTextures")&&v.contains("createInputProducer()")&&v.contains("PassBlur input producer was not replaced"));
    }

    @Test public void rootSurfaceReplacementSelfHealsInsteadOfSilentlyKeepingTheOldBinding() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(view.contains("!binding.rootSurface.isValid()"));
        assertTrue(view.contains("!isSameSurface(binding.rootSurface, geometry.rootSurface)"));
        assertTrue(view.contains("rebindProducer(\"producer-root-changed\")"));
    }
}
