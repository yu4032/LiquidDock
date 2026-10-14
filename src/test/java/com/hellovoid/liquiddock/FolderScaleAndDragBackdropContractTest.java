package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contracts for HyperOS 4.50 small-folder preview scaling and drag glass authority. */
public class FolderScaleAndDragBackdropContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void smallFolderPreviewContainerOwnsItsScaledRemeasureDomain() throws Exception {
        String hook = Files.readString(MAIN.resolve("Launcher450IconSizeHook.java"));

        assertTrue(hook.contains("FOLDER_PREVIEW_CONTAINER_1X1"));
        assertTrue(hook.contains("com.miui.home.launcher.folder.FolderIconPreviewContainer1X1"));
        assertTrue(hook.contains("installFolderPreviewMeasureTransaction"));
        assertTrue(hook.contains("\"onMeasure\""));
        assertTrue(hook.contains("MeasureDomain.FOLDER"));
    }

    @Test public void folderTraceBootstrapsBeforeOtherLauncherHooksAndCannotBeSilentlyGated()
            throws Exception {
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        String tracer = Files.readString(MAIN.resolve("Launcher450SmallFolderTrace.java"));
        String iconHook = Files.readString(MAIN.resolve("Launcher450IconSizeHook.java"));

        assertTrue(module.contains("Launcher450SmallFolderTrace.install(classLoader)"));
        assertTrue(module.indexOf("Launcher450SmallFolderTrace.install(classLoader)")
                < module.indexOf("Launcher450IconSizeHook.install(classLoader"));
        assertTrue(tracer.contains("LD-FolderTrace"));
        assertTrue(tracer.contains("v2-independent-20261014"));
        assertTrue(tracer.contains("install-enter"));
        assertTrue(tracer.contains("hook-installed FolderIcon1x1.onMeasure"));
        assertTrue(tracer.contains("hook-FAILED FolderIcon1x1.onMeasure"));
        assertTrue(tracer.contains("install-complete"));
        assertTrue(tracer.contains("android.util.Log.w("));
        assertTrue(tracer.contains("close-next-frame"));
        assertTrue(tracer.contains("vendor-setFolderIconBlur"));
        assertTrue(tracer.contains("drawable-bind-after"));
        assertFalse(tracer.contains("MainHook.debugLogging"));
        assertFalse(tracer.contains("MainHook.log("));
        assertFalse(tracer.contains("setLayoutParams("));
        assertFalse(tracer.contains("setClipChildren("));
        assertFalse(tracer.contains("setMiViewBlurMode("));
        assertFalse(tracer.contains("setImageDrawable("));
        // Diagnose the old geometry rather than reintroducing PR #372's
        // unvalidated MATCH_PARENT mutation.
        assertFalse(iconHook.contains("bindSmallFolderMaterialToIconSize("));
    }

    @Test public void dragGlassUsesIndependentUpperWorkspaceBackdropAuthority() throws Exception {
        String overlay = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        String source = Files.readString(MAIN.resolve("LauncherDragSourceOverlay.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));
        String domain = Files.readString(MAIN.resolve("PassBlurDomain.java"));
        String request = Files.readString(MAIN.resolve("PassBlurBindRequest.java"));

        assertTrue(source.contains("WindowManager.LayoutParams.TYPE_APPLICATION_PANEL"));
        assertTrue(source.contains("FLAG_NOT_TOUCHABLE"));
        assertTrue(source.contains("LiquidDockDragBackdropSource"));
        assertTrue(overlay.contains("LauncherDragSourceOverlay.attach"));
        assertTrue(domain.contains("DRAG_OVERLAY"));
        assertTrue(request.contains("static PassBlurBindRequest dragOverlay(View authoritativeRoot)"));
        assertTrue(overlay.contains("PassBlurBindRequest.dragOverlay(sourceOverlay)"));
        assertTrue(session.contains("PassBlurBindRequest bindRequest"));
        assertTrue(session.contains("source=RootPassBlurBackend domain="));
    }

    @Test public void liveDragPathNeverFreezesTheWorkspaceBackdrop() throws Exception {
        String overlay = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));
        String bridge = Files.readString(MAIN.resolve("LauncherLiveDragSessionBridge.java"));

        assertFalse(overlay.contains("freezeAfterNextFreshFrame"));
        assertFalse(overlay.contains("launcher-drag-frozen"));
        assertFalse(session.contains("freezeAfterNextFreshFrame"));
        assertFalse(session.contains("launcher-drag-frozen"));
        assertTrue(overlay.contains("prewarmLiveSource"));
        assertFalse(overlay.contains("gateCleanDragPresentation"));
        assertFalse(overlay.contains("requestCleanBackdropAndReveal"));
        assertTrue(session.contains("launcher-drag-live"));
        assertTrue(session.contains("setUpdatesEnabled(true"));
        assertTrue(session.contains("requestFreshBackdrop"));
        assertTrue(bridge.contains("session.ensureLiveDragSource()"));
    }

    @Test public void dragUpperWindowHostsBothLiveGlassAndVisualMirror() throws Exception {
        String overlay = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        String source = Files.readString(MAIN.resolve("LauncherDragSourceOverlay.java"));
        String mirror = Files.readString(MAIN.resolve("LauncherDragVisualMirror.java"));

        assertTrue(source.contains("glassHost"));
        assertTrue(source.contains("mirrorHost"));
        assertTrue(overlay.contains("glassHost()"));
        assertTrue(overlay.contains("LauncherDragVisualMirror.attach"));
        assertTrue(overlay.contains("mirror.syncFromDragView(source, sourceOverlay)"));
        assertTrue(mirror.contains("dragView.draw(canvas)"));
        assertTrue(mirror.contains("setWillNotDraw(false)"));
    }

    @Test public void dragMovementPublishesGlassAndMirrorFromTheChoreographerFrame() throws Exception {
        String overlay = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        assertTrue(overlay.contains("mirror.syncFromDragView(source, sourceOverlay)"));
        assertTrue(overlay.contains("publishFrameGeometry"));
        assertTrue(overlay.contains("liveSink.captureGeometry(authorityRoot)"));
        assertTrue(overlay.contains("authority.publishDragGeometry(liveSink, geometry)"));
        assertTrue(session.contains("void publishDragGeometry("));
        assertTrue(session.contains("node.geometry = geometry"));
        assertTrue(session.contains("requestDragRedraw()"));
    }

    @Test public void vendorDragViewIsSuppressedOnlyAfterUpperPresentationIsReady() throws Exception {
        String overlay = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        String mirror = Files.readString(MAIN.resolve("LauncherDragVisualMirror.java"));

        assertTrue(overlay.contains("claimVendorPresentation"));
        assertTrue(overlay.contains("restoreVendorPresentation"));
        assertTrue(overlay.contains("source.setAlpha(0f)"));
        assertTrue(overlay.contains("hasPreparedBackdrop"));
        assertTrue(overlay.contains("sink.isAvailable()"));
        assertTrue(mirror.contains("isReadyForPresentation"));
    }
}
