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
    public void dockUsesStandardSurfaceControlBlurAsClientCompositionHold() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));

        assertTrue("the hold must use SurfaceControl.Transaction, not MiUI View blur state",
                bridge.contains("\"setBackgroundBlurRadius\", SurfaceControl.class, Integer.TYPE"));
        assertTrue("only the Dock root should receive this discriminative composition hold",
                bridge.contains("domain == PassBlurDomain.DOCK"));
        assertTrue("the hold must stay visually minimal",
                bridge.contains("DOCK_CLIENT_COMPOSITION_HOLD_RADIUS_PX = 1"));
        assertTrue("bind must apply the standard background-blur field to the root SurfaceControl",
                bridge.contains("Integer.valueOf(DOCK_CLIENT_COMPOSITION_HOLD_RADIUS_PX)"));
        assertTrue("unbind must release the composition hold instead of leaking it",
                bridge.contains("transaction, binding.rootSurface, Integer.valueOf(0)"));
    }

}
