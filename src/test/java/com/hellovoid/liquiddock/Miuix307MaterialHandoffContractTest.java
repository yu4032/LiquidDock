package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contracts for immediate 307 material ownership and transient output recovery. */
public class Miuix307MaterialHandoffContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void attachBoundaryCanInstallBeforeFinalNativeGeometry() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));

        assertTrue("the native owner must be attachable before its final size/radius callback",
                hook.contains("static boolean canInstallBeforeGeometry(View dockBg)"));
        assertTrue("install must use the attached-parent boundary instead of a size gate",
                hook.contains("if (!canInstallBeforeGeometry(dockBg)) return false;"));
        assertTrue("the attach recovery must reach the installer during the placeholder phase",
                pipeline.contains("if (!MiuixGlassHook.canInstallBeforeGeometry(background))"));
        assertFalse("the pipeline must not defer the complete handoff until final geometry",
                pipeline.contains("if (!MiuixGlassHook.hasReadyNativeGeometry(background))"));
    }

    @Test
    public void bothNativeMaterialImplementationsAreHiddenAfterPrismalOwnership() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));

        assertTrue("default MiuiX material must not remain over the Prismal output",
                hook.contains("return isNativeVisualOwner(dockBg);"));
        assertTrue("the handoff must replace the vendor material body",
                hook.contains("vendor material body transparent"));
    }

    @Test
    public void transientInvalidMappingPreservesTheLastPresentedGlassFrame() throws Exception {
        String textureView = Files.readString(
                MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue("the renderer must remember whether a real frame reached the output surface",
                textureView.contains("private volatile boolean hasPresentedFrame;"));
        assertTrue("a transient OUTSIDE mapping must not clear an already visible frame",
                textureView.contains("if (hasPresentedFrame) return false;"));
    }

    @Test
    public void compositionAbPreservesVendorBlurWhilePassBlurSamplingRemainsActive() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));

        assertTrue("A/B build must clearly preserve native Dock blur",
                pipeline.contains("vendor Dock blur suppression BYPASSED"));
        assertFalse("A/B build must not install the hidden View blur suppressor",
                pipeline.contains("LauncherVendorBlurWriteSuppressor.install()"));
        assertFalse("A/B build must not install compat BlurUtilities suppression",
                pipeline.contains("installCompatBackgroundBlurSuppression(classLoader);"));
        assertTrue("PassBlur/Prismal installation must remain active",
                hook.contains("Miuix307ZeroCopyRenderer.install("));
        assertFalse("A/B build must not clear native pass-window blur",
                hook.contains("MiBlurBridge.clearPassWindowBlur(dockBg);"));
        assertTrue("material body remains transparent so geometry ownership stays unchanged",
                hook.contains("suppressVendorMaterialBody(background, readRadius(background));"));
    }
}
