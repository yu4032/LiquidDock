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
    public void vendorBlurIsSuppressedAtWriteBoundaryNotRootPreDraw() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        String suppressor = Files.readString(
                MAIN.resolve("LauncherVendorBlurWriteSuppressor.java"));
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));

        assertTrue("Launcher material ownership must install the hidden View write suppressor",
                pipeline.contains("LauncherVendorBlurWriteSuppressor.install()"));
        assertTrue(suppressor.contains("\"setPassWindowBlurEnabled\""));
        assertTrue(suppressor.contains("\"setMiViewBlurMode\""));
        assertTrue(suppressor.contains("\"setMiBackgroundBlurMode\""));
        assertTrue(suppressor.contains("\"setMiBackgroundBlurRadius\""));
        assertTrue("pre-draw may preserve the transparent vendor body",
                hook.contains("suppressVendorMaterialBody(background, readRadius(background));"));
        assertFalse("root pre-draw must never fight vendor compositor blur every frame",
                hook.contains("suppressVendorGpuBlur(background);\n"
                        + "                suppressVendorMaterialBody(background"));
    }

    @Test
    public void minimumPositiveBlurRegionKeepsWallpaperOnClientCompositionPath() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        String bridge = Files.readString(MAIN.resolve("MiBlurBridge.java"));
        String suppressor = Files.readString(
                MAIN.resolve("LauncherVendorBlurWriteSuppressor.java"));

        assertTrue("material handoff must retain native composition eligibility",
                hook.contains("MiBlurBridge.holdPassWindowBlurComposition(dockBg)"));
        assertTrue("runtime teardown must release the composition hold",
                hook.contains("MiBlurBridge.clearPassWindowBlur(background)"));

        assertTrue(bridge.contains("SET_PASS_WINDOW_BLUR_ENABLED.invoke(view, true)"));
        assertTrue(bridge.contains("SET_MI_VIEW_BLUR_MODE.invoke(view, 1)"));
        assertTrue(bridge.contains("SET_MI_BACKGROUND_BLUR_MODE.invoke(view, 1)"));
        assertTrue("force-client hold must use a real positive background-blur radius",
                bridge.contains("SET_MI_BACKGROUND_BLUR_RADIUS.invoke(view, 1)"));
        assertTrue(bridge.contains("CLEAR_MI_BACKGROUND_BLEND_COLOR.invoke(view)"));

        assertTrue("module-owned keepalive writes must bypass vendor suppression",
                suppressor.contains("beginInternalWrite()"));
        assertTrue("vendor disable writes must not be able to drop the keepalive",
                suppressor.contains("shouldSuppressPassWindowWrite"));
        assertTrue("vendor mode-zero writes must not be able to drop the keepalive",
                suppressor.contains("shouldSuppressBlurModeWrite"));
        assertTrue("vendor radius writes must not be able to remove or enlarge the hold",
                suppressor.contains("shouldSuppressBlurRadiusWrite"));
        assertTrue("compat BlurBackground2 must retain a real positive blur region",
                hook.contains("stabilizeCompatBackgroundBlurRadius"));
        assertTrue("compat force-client region must use the minimum radius",
                hook.contains("requestedRadius + \" -> 1\""));
    }
}
