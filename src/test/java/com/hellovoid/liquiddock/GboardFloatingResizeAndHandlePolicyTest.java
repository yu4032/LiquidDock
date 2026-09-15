package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contracts for floating resize symmetry and post-drag editing policy. */
public class GboardFloatingResizeAndHandlePolicyTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path CONFIG = MAIN.resolve("config/ConfigSchema.java");
    private static final Path SETTINGS = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/GboardSettingsPages.kt");

    private static String read(Path path) throws Exception {
        return Files.exists(path) ? Files.readString(path) : "";
    }

    @Test public void sinkOutputFollowsTransformedGeometryForShrinkAndGrow() throws Exception {
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));
        String geometry = read(MAIN.resolve("GboardFloatingGlassGeometry.java"));

        assertTrue(geometry.contains("outputWidthPx()"));
        assertTrue(geometry.contains("outputHeightPx()"));
        assertTrue(coordinator.contains("syncSinkBounds(state, next)"));
        assertTrue(coordinator.contains("next.outputWidthPx()"));
        assertTrue(coordinator.contains("next.outputHeightPx()"));
        assertFalse(coordinator.contains("syncSinkBounds(state);\n        GboardFloatingGlassGeometry next"));
    }

    @Test public void handleDragAutoResizePolicyIsStableLiveAndScoped() throws Exception {
        String policy = read(MAIN.resolve("GboardFloatingHandlePolicy.java"));
        String hook = read(MAIN.resolve("GboardFloatingGlassHook.java"));
        String preferences = read(MAIN.resolve("GboardGlassPreferences.java"));
        String schema = read(CONFIG);
        String settings = read(SETTINGS);

        assertTrue(schema.contains("AUTO_RESIZE_AFTER_HANDLE_DRAG"));
        assertTrue(schema.contains("liquid_gboard_auto_resize_after_handle_drag"));
        assertTrue(preferences.contains("AUTO_RESIZE_AFTER_HANDLE_DRAG_KEY"));
        assertTrue(policy.contains("View.OnTouchListener"));
        assertTrue(policy.contains("MotionEvent.ACTION_CANCEL"));
        assertTrue(policy.contains("ConfigReader.load()"));
        assertTrue(policy.contains("AUTO_RESIZE_AFTER_HANDLE_DRAG_KEY"));
        assertTrue(hook.contains("GboardFloatingHandlePolicy.install()"));
        assertTrue(hook.contains("GboardFloatingHandlePolicy.bind(structure.bottomFrame)"));
        assertTrue(settings.contains("拖动后自动进入大小调整"));
        assertFalse(policy.contains("\"pcb\""));
        assertFalse(policy.contains("\"pbu\""));
        assertFalse(policy.contains("\"pcg\""));
        assertFalse(policy.contains("0x7f"));
    }
}
