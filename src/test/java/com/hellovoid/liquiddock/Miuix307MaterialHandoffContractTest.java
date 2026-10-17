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
    public void nativeRadiusReflectionStaysOnGeometryBoundaries() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        String textureView = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue("geometry sync must forward the already-read native radius",
                hook.contains("Miuix307ZeroCopyRenderer.setNativeOpticsRadiusPx(nativeRadius)"));
        assertTrue("vendor-body pre-draw must reuse cached geometry instead of reflecting every frame",
                hook.contains("suppressVendorMaterialBody(background, cachedRadiusOrRead(background));"));
        assertTrue(renderer.contains("static void setNativeOpticsRadiusPx(float radiusPx)"));
        assertTrue(textureView.contains("private volatile float nativeOpticsRadiusPx = Float.NaN;"));
        assertTrue(textureView.contains("float nativeRadius = nativeOpticsRadiusPx;"));
        assertFalse("Dock EGL draw must not reflect vendor radius every frame",
                textureView.contains("MiuixGlassHook.readNativeOpticsRadius(materialHost)"));
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
        assertTrue("pre-draw may preserve the transparent vendor body from cached geometry",
                hook.contains("suppressVendorMaterialBody(background, cachedRadiusOrRead(background));"));
        assertFalse("root pre-draw must not reflect native radius every frame",
                hook.contains("suppressVendorMaterialBody(background, readRadius(background));"));
        assertFalse("root pre-draw must never fight vendor compositor blur every frame",
                hook.contains("suppressVendorGpuBlur(background);\n"
                        + "                suppressVendorMaterialBody(background"));
    }

    @Test
    public void folderHandoffCannotRestoreStaleDockPauseOrDoubleCompositeGlass() throws Exception {
        String dock = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String folder = Files.readString(MAIN.resolve("OpenFolderGlassCoordinator.java"));
        String layer = Files.readString(MAIN.resolve("ShortcutPopupGlassLayer.java"));

        assertTrue("render-thread capture must apply latest UI power policy",
                dock.contains("boolean latestEnabled = producerUpdatesEnabled;"));
        assertTrue(dock.contains("if (latestEnabled) Miuix307PassBlurBridge.resumeUpdates(current);"));
        assertFalse("older front-of-queue closures must not override a later resume",
                dock.contains("if (enabled) Miuix307PassBlurBridge.resumeUpdates(current);"));
        assertFalse("once the resident glass is presented, temporary glass cannot linger",
                folder.contains("state.layer.fadeIntoWorkspace("));
        assertFalse("obsolete 80-ms lens crossfade must be removed",
                layer.contains("void fadeIntoWorkspace("));
    }

    @Test
    public void expandedFolderCannotOwnDockProducerOrEglRenderGate() throws Exception {
        // Static architecture/API contract: OPEN_FOLDER has a separate capture owner
        // and cannot inject a render-freeze method into Dock's 307 pipeline.
        String folder = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));
        String scene = Files.readString(MAIN.resolve("LauncherGlassSceneController.java"));

        assertTrue(folder.contains("LauncherGlassSceneController.setFolderCoveredForAll(true)"));
        assertFalse("Modal folder must never install a Dock-specific freeze gate",
                folder.contains("Miuix307ZeroCopyRenderer.setFolderFrozen"));
        assertFalse("Dock must have no synthetic OPEN_FOLDER render-freeze API",
                renderer.contains("setFolderRenderFrozen"));
        assertFalse("Dock OES input must never be discarded due to FolderCling",
                view.contains("discardFrozenProducerFrame"));
        assertFalse("Dock EGL draw must not be gated by modal folder coverage",
                view.contains("folderRenderFrozen"));
        assertFalse("Recents must not restore a second synthetic Dock freeze authority",
                scene.contains("Miuix307ZeroCopyRenderer.setRecentsCovered"));
        assertFalse("Dock must not require a special post-folder restart transaction",
                bridge.contains("requestFreshDockFrameAfterFolder"));
        assertTrue("Native producer still keeps the frame-sync lease",
                view.contains("Miuix307PassBlurBridge.renewForceRefresh(binding)"));
        assertTrue("Frame callbacks must continue to request GL composition",
                view.contains("scheduleRender(true)"));
    }
}
