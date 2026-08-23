package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for the feedback-safe HyperOS 307 TextureView + EGL calibration backend. */
public class Miuix307TextureViewPassBlurCalibrationTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void activeRendererUsesTextureViewEglInsteadOfIndependentSurfaceView() throws Exception {
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        Path textureViewPath = MAIN.resolve("Miuix307PassBlurTextureView.java");

        assertTrue(Files.exists(textureViewPath));
        assertTrue(renderer.contains("Miuix307PassBlurTextureView"));
        assertFalse(renderer.contains("new Miuix307PassBlurGpuView"));

        String view = Files.readString(textureViewPath);
        assertTrue(view.contains("extends TextureView")
                && view.contains("implements TextureView.SurfaceTextureListener"));
        assertTrue(view.contains("EGL14.eglCreateWindowSurface"));
        assertTrue(view.contains("GLES11Ext.GL_TEXTURE_EXTERNAL_OES")
                && view.contains("new SurfaceTexture(oesTexture)"));
        assertTrue(view.contains("inputSurfaceTexture")
                && view.contains("outputSurfaceTexture")
                && view.contains("outputWindowSurface"));
    }

    @Test public void fullUpstreamPrismalOpticsRunAfterOesNormalizationWithoutLeavingGpuPath() throws Exception {
 String v=Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java")),a=Files.readString(MAIN.resolve("Miuix307PassBlurShaders.java")); assertTrue(v.contains("OES_NORMALIZE_FRAGMENT")&&v.contains("prismalRenderer.prepareBackdrop(")&&v.contains("dockCompositor.drawFrame(")); assertTrue(a.contains("samplerExternalOES uTexture")&&a.contains("uBackdropRect")&&a.contains("uTexMatrix")); assertFalse(v.contains("glReadPixels")||v.contains("captureScreenAsync"));
    }

    @Test
    public void existingLiquidDockGlassConfigDrivesPrismalMaterialAtInstallAndSync() throws Exception {
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String material = Files.readString(MAIN.resolve("Miuix307PrismalMaterial.java"));
        String migration = Files.readString(MAIN.resolve("config/ConfigMigration.java"));

        assertTrue(material.contains("static Params fromConfig(LiquidDockConfig.Glass glass, float density)"));
        assertTrue("current parameter semantics must be direct",
                material.contains("glass.thickness * d")
                        && material.contains("glass.prismalHeightTransitionWidth * d")
                        && material.contains("glass.lensRefraction")
                        && material.contains("glass.depthEffect")
                        && material.contains("p.lensDepthEffect"));
        assertFalse("legacy /12 lens conversion must not run in material updates",
                material.contains("glass.lensRefraction / 12f"));
        assertFalse("historical lens values must no longer be numerically migrated",
                migration.contains("prismalLensScale")
                        || migration.contains("legacyValue / 12f"));
        assertTrue("unsupported glass generations must reset to current schema defaults",
                migration.contains("resetUnsupportedGlassConfigGeneration(preferences)"));
        assertFalse("old depthEffect compatibility multiplier must not alter upstream lens depth",
                material.contains("glass.depthEffect / 0.08f"));
        assertTrue(material.contains("glass.ior")
                && material.contains("glass.normalStrength")
                && material.contains("glass.dome")
                && material.contains("glass.chromatic")
                && material.contains("glass.highlightWidth")
                && material.contains("glass.brightness")
                && material.contains("glass.specularSharp")
                && material.contains("glass.specularStrength")
                && material.contains("glass.rimLight")
                && material.contains("glass.caustics")
                && material.contains("glass.tintR")
                && material.contains("glass.tintG")
                && material.contains("glass.tintB")
                && material.contains("glass.tintAlpha")
                && material.contains("glass.blur"));
        assertTrue(view.contains("void setGlassConfig(LiquidDockConfig.Glass glassConfig)")
                && view.contains("Miuix307PrismalMaterial.fromConfig"));
        assertTrue(view.contains("if (hasConsumedFrame) renderHandler.post(() -> drawLatestFrame(false))"));
        assertTrue(renderer.contains("gpuBackdrop.setGlassConfig(glassConfig)"));
        int sync = renderer.indexOf("static void sync(LiquidDockConfig.Glass glassConfig, int blurRadiusPx)");
        assertTrue(sync >= 0);
        String syncRegion = renderer.substring(sync);
        assertTrue(syncRegion.contains("gpuBackdrop.setGlassConfig(glassConfig)"));
        assertFalse(syncRegion.contains("new Miuix307PassBlurTextureView")
                || syncRegion.contains("Miuix307PassBlurBridge.unbind"));
    }

    @Test
    public void passBlurBridgeNoLongerDependsOnChildSurfaceViewExclusion() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));
        assertTrue(bridge.contains("static Binding bind(")
                && bridge.contains("View materialHost, Surface producerSurface, float requestedScale"));
        assertFalse(bridge.contains("SurfaceView")
                || bridge.contains("outputView.getSurfaceControl()"));
        assertTrue(bridge.contains("String[] exclusions") && bridge.contains("rootName"));
    }

    @Test public void calibrationRotationReplacesInputProducerGeneration() throws Exception {
 String v=Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java")); assertTrue(v.contains("replaceProducerGeneration")&&v.contains("producer-generation-changed")&&v.contains("releaseInputProducer")&&v.contains("createInputProducer()"));
    }

    @Test
    public void validationTimeoutFailsClosedWithoutLegacyCapture() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        int validation = hook.indexOf("private static void scheduleZeroCopyValidation");
        assertTrue(validation >= 0);
        String validationRegion = hook.substring(validation);
        assertTrue(validationRegion.contains("glass remains transparent"));
        assertFalse(hook.contains("installCaptureFallback")
                || hook.contains("LiquidGlassFactory")
                || hook.contains("DockLiquidGlassView"));
    }
}
