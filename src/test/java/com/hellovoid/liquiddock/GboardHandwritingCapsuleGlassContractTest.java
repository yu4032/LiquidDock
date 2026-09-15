package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static contract for replacing only the Gboard handwriting toolbar background. */
public class GboardHandwritingCapsuleGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(String name) throws Exception {
        Path path = MAIN.resolve(name);
        return Files.exists(path) ? Files.readString(path) : "";
    }

    @Test public void gboardProcessInstallsStableCompanionToolbarHook() throws Exception {
        String module = read("ModuleMain.java");
        String hook = read("GboardHandwritingCapsuleGlassHook.java");
        assertTrue(module.contains("GboardHandwritingCapsuleGlassHook.install(classLoader)"));
        assertTrue(hook.contains(
                "com.google.android.libraries.inputmethod.companionwidget.widget.WidgetSoftKeyboardView"));
        assertTrue(hook.contains("\"onLayout\""));
        assertTrue(hook.contains("GboardHandwritingToolbarGeometryPolicy.isToolbar"));
        assertTrue(hook.contains("GboardGlassPreferences.resolve"));
        assertFalse(hook.contains("HandwritingOverlayView"));
        assertFalse(hook.contains("handwritingSceneActive"));
        assertFalse(hook.contains("0x7f"));
        assertFalse(hook.contains("findViewById"));
        assertFalse(hook.contains("postDelayed"));
    }

    @Test public void toolbarUsesCapsuleRadiusFromRuntimeGeometryNotResourceNames() throws Exception {
        String hook = read("GboardHandwritingCapsuleGlassHook.java");
        String coordinator = read("GboardHandwritingCapsuleGlassCoordinator.java");
        assertTrue(hook.contains("toolbarCornerRadiusPx(host)"));
        assertTrue(hook.contains("Math.min(host.getWidth(), host.getHeight()) * 0.5f"));
        assertTrue(hook.contains("GboardHandwritingCapsuleGlassCoordinator.onShown("));
        assertTrue(hook.contains("liveConfig.glass"));
        assertTrue(hook.contains("cornerRadiusPx"));
        assertTrue(coordinator.contains("float nativeRadiusPx"));
        assertFalse(hook.contains("AttributeSet"));
        assertFalse(hook.contains("getIdentifier("));
        assertFalse(hook.contains("obtainStyledAttributes"));
        assertFalse(hook.contains("NATIVE_TOOLBAR_RADII"));
        assertFalse(hook.contains("100f"));
        assertFalse(hook.contains("0x7f"));
    }

    @Test public void toolbarGlassOwnsSilhouetteWithoutVendorOrViewBackground() throws Exception {
        String hook = read("GboardHandwritingCapsuleGlassHook.java");
        String coordinator = read("GboardHandwritingCapsuleGlassCoordinator.java");
        assertTrue(hook.contains(
                "com.google.android.libraries.inputmethod.widgets.ShadowedSoftKeyboardView"));
        assertTrue(hook.contains("\"draw\""));
        assertTrue(hook.contains("Canvas.class"));
        assertTrue(hook.contains("resolveDispatchDraw"));
        assertTrue(hook.contains("dispatchDraw.invoke(owner, canvas)"));
        assertTrue(hook.contains("GboardHandwritingCapsuleGlassCoordinator.isActive"));
        assertFalse(hook.contains("superDrawBridge"));
        assertFalse(hook.contains("getDeclaredMethod(\"m\""));
        assertTrue(coordinator.contains("static synchronized boolean isActive"));
    }

    @Test public void toolbarGlassUsesUnclippedSiblingOutputWithRasterGuard() throws Exception {
        String coordinator = read("GboardHandwritingCapsuleGlassCoordinator.java");
        String geometry = read("GboardFloatingGlassGeometry.java");
        assertTrue(coordinator.contains("findUnclippedSinkHost"));
        assertTrue(coordinator.contains("sinkHost.addView(sink, branchIndex"));
        assertFalse(coordinator.contains("state.host.addView(sink, 0"));
        assertTrue(coordinator.contains("RASTER_GUARD_PX"));
        assertTrue(coordinator.contains("captureTargetPadded"));
        assertTrue(geometry.contains("captureTargetPadded"));
        assertTrue(geometry.contains("cropLeft"));
        assertTrue(geometry.contains("cropTop"));
        assertTrue(geometry.contains("cropWidth"));
        assertTrue(geometry.contains("cropHeight"));
    }

    @Test public void toolbarRemovesOnlySemanticBodyBackgroundAfterPresentationAndRestoresIt() throws Exception {
        String coordinator = read("GboardHandwritingCapsuleGlassCoordinator.java");
        assertTrue(coordinator.contains(".widget-keyboard.keyboard-body-area"));
        assertTrue(coordinator.contains("stockBodyBackground"));
        assertTrue(coordinator.contains("findToolbarBody"));
        assertTrue(coordinator.contains("bodyArea.setBackground(null)"));
        assertTrue(coordinator.contains("bodyArea.setBackground(state.stockBodyBackground)"));
        assertFalse(coordinator.contains("removeAllViews"));
        assertFalse(coordinator.contains("findViewById"));
    }

    @Test public void toolbarRebuildsSessionWhenGuiConfigSnapshotChanges() throws Exception {
        String reader = read("ConfigReader.java");
        String hook = read("GboardHandwritingCapsuleGlassHook.java");
        String coordinator = read("GboardHandwritingCapsuleGlassCoordinator.java");
        assertTrue(reader.contains("snapshotHash()"));
        assertTrue(hook.contains("liveReader.snapshotHash()"));
        assertTrue(coordinator.contains("configSnapshotHash"));
        assertTrue(coordinator.contains("existing.configSnapshotHash != configSnapshotHash"));
        assertTrue(coordinator.contains("release(existing)"));
    }

    @Test public void toolbarDoesNotChangeValidatedFloatingCompositeAlphaContract() throws Exception {
        String composite = read("Miuix307PrismalCompositeShaders.java");
        assertTrue(composite.contains("gl_FragColor = texture2D(uTexture, uv)"));
        assertFalse(composite.contains("sample.rgb * sample.a"));
    }

    @Test public void toolbarCoordinatorKeepsVendorControlsAndPlacesGlassUnderThem() throws Exception {
        String coordinator = read("GboardHandwritingCapsuleGlassCoordinator.java");
        assertTrue(coordinator.contains("GboardFloatingGlassView"));
        assertTrue(coordinator.contains("GboardFloatingGlassSession"));
        assertTrue(coordinator.contains("GboardFloatingGlassGeometry.captureTargetPadded"));
        assertFalse(coordinator.contains("removeAllViews"));
        assertFalse(coordinator.contains("findViewById"));
    }

    @Test public void toolbarTargetUsesRuntimeGeometryAndFailsClosed() throws Exception {
        String hook = read("GboardHandwritingCapsuleGlassHook.java");
        String coordinator = read("GboardHandwritingCapsuleGlassCoordinator.java");
        assertTrue(hook.contains("isToolbarGeometry"));
        assertTrue(hook.contains("getRootView()"));
        assertTrue(hook.contains("getWidth()"));
        assertTrue(hook.contains("getHeight()"));
        assertTrue(coordinator.contains("onHidden"));
        assertTrue(coordinator.contains("release(state)"));
        assertTrue(coordinator.contains("onFailure"));
        assertFalse(coordinator.contains("postDelayed"));
    }

    @Test public void genericGboardGeometryCanCaptureSingleTargetWithoutResourceIds() throws Exception {
        String geometry = read("GboardFloatingGlassGeometry.java");
        assertTrue(geometry.contains("captureTarget("));
        assertTrue(geometry.contains("mapBounds(target"));
        assertFalse(geometry.contains("0x7f"));
    }
}
