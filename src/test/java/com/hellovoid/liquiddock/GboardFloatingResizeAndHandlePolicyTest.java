package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contracts for floating resize symmetry and post-drag interaction policy. */
public class GboardFloatingResizeAndHandlePolicyTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path SETTINGS = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/GboardSettingsPages.kt");
    private static final Path COMPOSE_SETTINGS = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");

    private static String read(Path path) throws Exception {
        return Files.exists(path) ? Files.readString(path) : "";
    }

    @Test public void sinkWidthUsesStockShellButHeightUsesVisualContentUnion() throws Exception {
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));
        String geometry = read(MAIN.resolve("GboardFloatingGlassGeometry.java"));

        assertTrue(geometry.contains("structure.stockBackground"));
        assertTrue(geometry.contains("structure.topEdge"));
        assertTrue(geometry.contains("structure.keyboardViewHolders"));
        assertTrue(geometry.contains("structure.bottomFrame"));
        assertTrue(geometry.contains("addVerticalAuthority"));
        assertTrue(geometry.contains("sinkHost.transformMatrixToGlobal"));
        assertTrue(geometry.contains("sinkWidthPx()"));
        assertTrue(geometry.contains("sinkHeightPx()"));
        assertTrue(coordinator.contains("state.root, state.sinkHost, state.structure, state.cornerRadiusPx"));
        assertTrue(coordinator.contains("syncSinkBounds(state, next)"));
        assertTrue(coordinator.contains("geometry.sinkWidthPx()"));
        assertTrue(coordinator.contains("geometry.sinkHeightPx()"));
        assertTrue(coordinator.contains("geometry.sinkLeft"));
        assertTrue(coordinator.contains("geometry.sinkTop"));
        assertFalse(coordinator.contains("int height = state.backgroundFrame.getHeight()"));
        assertFalse(coordinator.contains("int height = state.keyboardArea.getHeight()"));
    }

    @Test public void handleDragPoliciesAreStableLiveAndScoped() throws Exception {
        String policy = read(MAIN.resolve("GboardFloatingHandlePolicy.java"));
        String hook = read(MAIN.resolve("GboardFloatingGlassHook.java"));
        String preferences = read(MAIN.resolve("GboardGlassPreferences.java"));
        String settings = read(SETTINGS);

        assertTrue(preferences.contains("AUTO_RESIZE_AFTER_HANDLE_DRAG_KEY"));
        assertTrue(preferences.contains("liquid_gboard_auto_resize_after_handle_drag"));
        assertTrue(preferences.contains("AUTO_RESIZE_AFTER_HANDLE_DRAG_DEFAULT = true"));
        assertTrue(preferences.contains("BOTTOM_DOCKING_KEY"));
        assertTrue(preferences.contains("liquid_gboard_bottom_docking"));
        assertTrue(preferences.contains("BOTTOM_DOCKING_DEFAULT = true"));
        assertTrue(policy.contains("View.OnTouchListener"));
        assertTrue(policy.contains("MotionEvent.ACTION_CANCEL"));
        assertTrue(policy.contains("MotionEvent.obtain(event)"));
        assertTrue(policy.contains("ConfigReader.load()"));
        assertTrue(policy.contains("AUTO_RESIZE_AFTER_HANDLE_DRAG_KEY"));
        assertTrue(policy.contains("BOTTOM_DOCKING_KEY"));
        assertTrue(policy.contains(".icon.floating_keyboard_dock_hint_v2"));
        assertTrue(policy.contains("findViewById"));
        assertTrue(policy.contains("ThreadLocal"));
        assertTrue(policy.contains("shouldMaskDockHitResult"));
        assertFalse(policy.contains("shouldSuppressDockMove"));
        assertFalse(policy.contains("dockZoneTop"));
        assertFalse(policy.contains("dockGestureSuppressed"));
        assertTrue(hook.contains("GboardFloatingHandlePolicy.install()"));
        assertTrue(hook.contains("GboardFloatingHandlePolicy.bind(structure.bottomFrame)"));
        assertTrue(settings.contains("拖动后自动进入大小调整"));
        assertTrue(settings.contains("拖到底部切换为全尺寸键盘"));
        assertFalse(policy.contains("\"pcb\""));
        assertFalse(policy.contains("\"pbu\""));
        assertFalse(policy.contains("\"peh\""));
        assertFalse(policy.contains("\"pcg\""));
        assertFalse(policy.contains("0x7f"));
    }

}
