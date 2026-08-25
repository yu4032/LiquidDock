package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Runtime contract for reversible non-glass visual ownership switches. */
public class VisualRuntimeToggleContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void processStateTracksOnlyRuntimeSafeVisualSwitches() throws Exception {
        Path path = MAIN.resolve("VisualRuntimeState.java");
        assertTrue(Files.exists(path));
        String state = Files.readString(path);
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));

        assertTrue(state.contains("ConfigSchema.Dock.ENABLED.name()"));
        assertTrue(state.contains("ConfigSchema.Dock.STROKE_ENABLED.name()"));
        assertTrue(state.contains("ConfigSchema.Dock.SHADOW_ENABLED.name()"));
        assertTrue(state.contains("ConfigSchema.Dock.STROKE_SHADOW.name()"));
        assertTrue(state.contains("ConfigSchema.Divider.ENABLED.name()"));
        assertFalse(state.contains("ConfigSchema.Workstation.DOCK_CUSTOMIZATION.name()"));

        assertTrue(state.contains("static boolean isDockCustomizationEnabled()"));
        assertTrue(state.contains("static boolean isDockStrokeEnabled()"));
        assertTrue(state.contains("static boolean isDockShadowEnabled()"));
        assertTrue(state.contains("static boolean isStrokeShadowEnabled()"));
        assertTrue(state.contains("static boolean isDividerEnabled()"));

        assertTrue(module.contains("VisualRuntimeState.initialize"));
        assertTrue(module.contains("runtimeConfig.dock.enabled"));
        assertTrue(module.contains("runtimeConfig.dock.strokeEnabled"));
        assertTrue(module.contains("runtimeConfig.dock.shadowEnabled"));
        assertTrue(module.contains("runtimeConfig.dock.strokeShadow"));
        assertTrue(module.contains("runtimeConfig.divider.enabled"));
    }

    @Test
    public void strokeShapeModeSwitchesRefreshExistingRendererOwnership() throws Exception {
        String state = Files.readString(MAIN.resolve("VisualRuntimeState.java"));

        assertTrue(state.contains("ConfigSchema.Dock.SQUIRCLE.name()"));
        assertTrue(state.contains("ConfigSchema.Dock.FILL_DIFF.name()"));
        assertTrue(state.contains("strokeStyleChanged"));
        assertTrue(state.contains("DockStrokeRenderer.refreshInstalledFromCurrentConfig()"));
    }
}
