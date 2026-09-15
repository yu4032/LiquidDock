package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contracts for floating resize symmetry and post-drag editing policy. */
public class GboardFloatingResizeAndHandlePolicyTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path SETTINGS = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/GboardSettingsPages.kt");

    private static String read(Path path) throws Exception {
        return Files.exists(path) ? Files.readString(path) : "";
    }

    @Test public void sinkOutputFollowsResizedStockShellForShrinkAndGrow() throws Exception {
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));
        String geometry = read(MAIN.resolve("GboardFloatingGlassGeometry.java"));

        assertTrue(geometry.contains("shellView.transformMatrixToGlobal"));
        assertTrue(geometry.contains("shellView.getWidth()"));
        assertTrue(geometry.contains("shellView.getHeight()"));
        assertTrue(coordinator.contains("state.root, state.backgroundFrame, state.cornerRadiusPx"));
        assertTrue(coordinator.contains("int width = state.backgroundFrame.getWidth()"));
        assertTrue(coordinator.contains("int height = state.backgroundFrame.getHeight()"));
        assertTrue(coordinator.contains("state.backgroundFrame.getLocationInWindow(shellLocation)"));
        assertFalse(coordinator.contains("int width = state.keyboardArea.getWidth()"));
        assertFalse(coordinator.contains("int height = state.keyboardArea.getHeight()"));
    }

    @Test public void handleDragAutoResizePolicyIsStableLiveAndScoped() throws Exception {
        String policy = read(MAIN.resolve("GboardFloatingHandlePolicy.java"));
        String hook = read(MAIN.resolve("GboardFloatingGlassHook.java"));
        String preferences = read(MAIN.resolve("GboardGlassPreferences.java"));
        String settings = read(SETTINGS);

        assertTrue(preferences.contains("AUTO_RESIZE_AFTER_HANDLE_DRAG_KEY"));
        assertTrue(preferences.contains("liquid_gboard_auto_resize_after_handle_drag"));
        assertTrue(preferences.contains("AUTO_RESIZE_AFTER_HANDLE_DRAG_DEFAULT = true"));
        assertTrue(policy.contains("View.OnTouchListener"));
        assertTrue(policy.contains("MotionEvent.ACTION_CANCEL"));
        assertTrue(policy.contains("MotionEvent.obtain(event)"));
        assertTrue(policy.contains("ConfigReader.load()"));
        assertTrue(policy.contains("AUTO_RESIZE_AFTER_HANDLE_DRAG_KEY"));
        assertTrue(hook.contains("GboardFloatingHandlePolicy.install()"));
        assertTrue(hook.contains("GboardFloatingHandlePolicy.bind(structure.bottomFrame)"));
        assertTrue(settings.contains("拖动后自动进入大小调整"));
        assertFalse(policy.contains("\"pcb\""));
        assertFalse(policy.contains("\"pbu\""));
        assertFalse(policy.contains("\"peh\""));
        assertFalse(policy.contains("\"pcg\""));
        assertFalse(policy.contains("0x7f"));
    }
}
