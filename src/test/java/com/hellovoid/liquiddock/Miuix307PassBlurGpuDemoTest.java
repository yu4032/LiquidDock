package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contracts for the HyperOS 307 PassBlur -> OES -> 2D -> Prismal backend. */
public class Miuix307PassBlurGpuDemoTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void bridgeBindsPassBlurProducerAtFullScale() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));
        assertTrue(bridge.contains("SetPassBlurSurface"));
        assertTrue(bridge.contains("setUpdateTextureFlag"));
        assertTrue(bridge.contains("requestedScale"));
        assertTrue(bridge.contains("View materialHost, Surface producerSurface, float requestedScale"));
    }

    @Test public void activeRendererUsesTextureViewEgl() throws Exception {
 String r=Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java")),v=Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java")); assertTrue(r.contains("new Miuix307PassBlurTextureView")); assertTrue(v.contains("extends TextureView")&&v.contains("eglCreateWindowSurface")&&v.contains("GL_TEXTURE_EXTERNAL_OES")&&v.contains("renderNormalizationPass")&&v.contains("prismalRenderer.prepareBackdrop(")&&v.contains("dockCompositor.drawFrame(")&&v.contains("renderCompositePass"));
    }

    @Test
    public void rootExclusionAvoidsTextureViewFeedbackLoop() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));
        assertTrue(bridge.contains("rootName"));
        assertFalse(bridge.contains("outputView.getSurfaceControl()"));
        assertFalse(bridge.contains("SurfaceView"));
    }

    @Test
    public void producerUsesRealViewRootSurfaceAndSurfaceSize() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        assertTrue(view.contains("mSurfaceSize"));
        assertTrue(view.contains("getSurfaceControl"));
        assertTrue(view.contains("SurfaceControl rootSurface"));
        assertTrue(view.contains("surfaceWidth"));
        assertTrue(view.contains("surfaceHeight"));
    }

    @Test
    public void bufferGeometryTracksConfigRotation() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        assertTrue(view.contains("if (nextRotation == 1 || nextRotation == 3)"));
        assertTrue(view.contains("bufferWidth = surfaceHeight"));
        assertTrue(view.contains("bufferHeight = surfaceWidth"));
        assertTrue(view.contains("setDefaultBufferSize(geometry.bufferWidth, geometry.bufferHeight)"));
    }

    @Test public void rotationReplacesProducerGenerationInsteadOfReusingOldBufferQueue() throws Exception {
 String v=Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java")); assertTrue(v.contains("replaceProducerGeneration")&&v.contains("producer-generation-changed")&&v.contains("recreateInputProducer")&&v.contains("releaseInputProducer")&&v.contains("createInputProducer()"));
    }

    @Test
    public void activeTextureViewHasNoIndependentSurfaceControlShapeHack() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        assertFalse(view.contains("setWindowCrop"));
        assertFalse(view.contains("setCornerRadius"));
        assertFalse(view.contains("getSurfaceControl()"));
    }

    @Test public void stageBIsolatedBeforeUpstreamPrismalMaterial() throws Exception {
 String v=Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java")),a=Files.readString(MAIN.resolve("Miuix307PassBlurShaders.java")),p=Files.readString(Path.of("prismal/src/main/res/raw/prismal_fragment.glsl")); assertTrue(a.contains("uConfigRot")&&a.contains("uTexMatrix")); assertTrue(v.contains("OES_NORMALIZE_FRAGMENT")&&v.contains("prismalRenderer.prepareBackdrop(")&&v.contains("dockCompositor.drawFrame(")); assertFalse(p.contains("samplerExternalOES")||p.contains("uBackdropRect"));
    }

    @Test
    public void stageBDiagnosticsExposeTextureMatrixCoverageAndHostGeometry() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        assertTrue(view.contains("texture matrix=") && view.contains("formatTextureMatrix"));
        assertTrue(view.contains("stage-B mapping rootScreen="));
        assertTrue(view.contains("hostScreen="));
        assertTrue(view.contains("hostSize="));
        assertTrue(view.contains("rootSurface="));
        assertTrue(view.contains("backdropRect="));
        assertTrue(view.contains("validDockRect="));
        assertTrue(view.contains("coverage="));
        assertTrue(view.contains("mapped corners"));
        assertTrue(view.contains("configRot="));
    }

    @Test
    public void validationTimeoutFailsClosedWithoutCaptureFallback() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        assertTrue(hook.contains("private static void scheduleZeroCopyValidation"));
        assertTrue(hook.contains("glass remains transparent"));
        assertFalse(hook.contains("installCaptureFallback"));
        assertFalse(hook.contains("LiquidGlassFactory"));
    }
}
