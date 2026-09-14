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
        assertTrue(overlay.contains("LauncherGlassSinkView.attachToExternalMaterial"));
        assertFalse(overlay.contains("LauncherGlassSinkView.attachToMaterial(\n                    carrier"));
        assertTrue(session.contains("PassBlurBindRequest bindRequest"));
        assertTrue(session.contains("source=RootPassBlurBackend domain="));
    }

    @Test public void newlyRegisteredDragSinkAlwaysCapturesInitialGeometry() throws Exception {
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        assertTrue(session.contains(
                "if (!rootGeometryChanged && !localChanged && node.geometry != null) continue;"));
        assertTrue(session.contains("LauncherGlassGeometry.Snapshot observed = sink.captureGeometry(root);"));
    }

    @Test public void desktopSingleDragCapturesAfterSourceRemovalBeforeDragViewPresentation()
            throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherDragOverlayHook.java"));
        String overlay = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        assertTrue(hook.contains("DRAG_CONTROLLER"));
        assertTrue(hook.contains("\"createDragView\""));
        assertTrue(hook.contains("dragAction == 0 && dragCount == 1"));
        assertTrue(hook.contains("prepareCleanCapture"));
        assertTrue(hook.contains("armCleanCapture"));
        assertTrue(hook.contains("\"showWithAnim\""));
        assertTrue(hook.contains("gateCleanDragPresentation"));
        assertTrue(hook.contains("requestCleanBackdropAndReveal"));
        assertTrue(overlay.contains("dragView.setAlpha(0f)"));
        assertTrue(overlay.contains("session.freezeAfterNextFreshFrame("));
        assertTrue(overlay.contains("restoreCleanDragPresentation"));
        assertTrue(session.contains("freezeAfterNextFreshFrameCallback"));
        assertTrue(session.contains("freezeAfterNextFreshFrameFailureCallback"));
    }

    @Test public void dragMovementPublishesCropGeometryFromTheChoreographerFrame() throws Exception {
        String overlay = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        assertTrue(overlay.contains("publishFrameGeometry"));
        assertTrue(overlay.contains("liveSink.captureGeometry(authorityRoot)"));
        assertTrue(overlay.contains("authority.publishDragGeometry(liveSink, geometry)"));
        assertTrue(session.contains("void publishDragGeometry("));
        assertTrue(session.contains("node.geometry = geometry"));
        assertTrue(session.contains("requestDragRedraw()"));
    }
}
