package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static contract for replacing only the Gboard handwriting companion capsule background. */
public class GboardHandwritingCapsuleGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(String name) throws Exception {
        Path path = MAIN.resolve(name);
        return Files.exists(path) ? Files.readString(path) : "";
    }

    @Test public void gboardProcessInstallsStableCompanionWidgetHook() throws Exception {
        String module = read("ModuleMain.java");
        String hook = read("GboardHandwritingCapsuleGlassHook.java");
        assertTrue(module.contains("GboardHandwritingCapsuleGlassHook.install(classLoader)"));
        assertTrue(hook.contains(
                "com.google.android.libraries.inputmethod.companionwidget.widget.WidgetSoftKeyboardView"));
        assertTrue(hook.contains(
                "com.google.android.apps.inputmethod.libs.handwriting.keyboard.HandwritingOverlayView"));
        assertTrue(hook.contains("\"onLayout\""));
        assertTrue(hook.contains("\"onDetachedFromWindow\""));
        assertTrue(hook.contains("handwritingSceneActive"));
        assertTrue(hook.contains("GboardGlassPreferences.resolve"));
        assertFalse(hook.contains("0x7f"));
        assertFalse(hook.contains("findViewById"));
        assertFalse(hook.contains("postDelayed"));
    }

    @Test public void capsuleCoordinatorKeepsVendorControlsAndPlacesGlassUnderThem() throws Exception {
        String coordinator = read("GboardHandwritingCapsuleGlassCoordinator.java");
        assertTrue(coordinator.contains("host.addView(sink, 0"));
        assertTrue(coordinator.contains("GboardFloatingGlassView"));
        assertTrue(coordinator.contains("GboardFloatingGlassSession"));
        assertTrue(coordinator.contains("GboardFloatingGlassGeometry.captureTarget"));
        assertTrue(coordinator.contains("onHandwritingSceneEnded"));
        assertFalse(coordinator.contains("removeAllViews"));
        assertFalse(coordinator.contains("setBackground(null)"));
        assertFalse(coordinator.contains("findViewById"));
    }

    @Test public void capsuleTargetIsSelectedByRuntimeGeometryAndFailsClosed() throws Exception {
        String hook = read("GboardHandwritingCapsuleGlassHook.java");
        String coordinator = read("GboardHandwritingCapsuleGlassCoordinator.java");
        assertTrue(hook.contains("isSideCapsuleGeometry"));
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
