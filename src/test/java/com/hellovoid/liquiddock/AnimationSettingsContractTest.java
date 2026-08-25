package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.liquiddock.config.PresetManager;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class AnimationSettingsContractTest {
    private static final Path MAIN = Path.of("src/main");

    @Test public void animationDurationsHaveStableDefaultsAndZeroToTwoSecondRange() {
        assertEquals(Integer.valueOf(450), ConfigSchema.Animation.WORKSPACE_VISIBILITY.uiDefault());
        assertEquals(Integer.valueOf(450), ConfigSchema.Animation.DOCK_ICON_REVEAL.uiDefault());
        assertEquals(Integer.valueOf(90), ConfigSchema.Animation.PRESS_IN.uiDefault());
        assertEquals(Integer.valueOf(160), ConfigSchema.Animation.PRESS_OUT.uiDefault());
        assertEquals(Integer.valueOf(180), ConfigSchema.Animation.DOCK_RESIZE.uiDefault());
        assertEquals(Integer.valueOf(300), ConfigSchema.Animation.SETTINGS_PAGE.uiDefault());
        assertEquals(Integer.valueOf(0), ConfigSchema.Animation.WORKSPACE_VISIBILITY.minInt());
        assertEquals(Integer.valueOf(2000), ConfigSchema.Animation.WORKSPACE_VISIBILITY.maxInt());
    }

    @Test public void guiHasIndependentAnimationPageAndAllSixControls() throws Exception {
        String ui = Files.readString(MAIN.resolve(
                "kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        assertTrue(ui.contains("Page.Animation"));
        assertTrue(ui.contains("private fun AnimationPage("));
        assertTrue(ui.contains("ConfigSchema.Animation.WORKSPACE_VISIBILITY"));
        assertTrue(ui.contains("ConfigSchema.Animation.DOCK_ICON_REVEAL"));
        assertTrue(ui.contains("ConfigSchema.Animation.PRESS_IN"));
        assertTrue(ui.contains("ConfigSchema.Animation.PRESS_OUT"));
        assertTrue(ui.contains("ConfigSchema.Animation.DOCK_RESIZE"));
        assertTrue(ui.contains("ConfigSchema.Animation.SETTINGS_PAGE"));
    }

    @Test public void defaultPresetCarriesAnimationTimings() {
        assertEquals(450, PresetManager.defaultValues().get(
                ConfigSchema.Animation.WORKSPACE_VISIBILITY.name()));
        assertEquals(450, PresetManager.defaultValues().get(
                ConfigSchema.Animation.DOCK_ICON_REVEAL.name()));
        assertEquals(90, PresetManager.defaultValues().get(ConfigSchema.Animation.PRESS_IN.name()));
        assertEquals(160, PresetManager.defaultValues().get(ConfigSchema.Animation.PRESS_OUT.name()));
        assertEquals(180, PresetManager.defaultValues().get(ConfigSchema.Animation.DOCK_RESIZE.name()));
        assertEquals(300, PresetManager.defaultValues().get(ConfigSchema.Animation.SETTINGS_PAGE.name()));
    }

    @Test public void runtimeAnimationConsumersUseTypedConfiguration() throws Exception {
        String config = Files.readString(MAIN.resolve(
                "java/com/hellovoid/liquiddock/LiquidDockConfig.java"));
        String transition = Files.readString(MAIN.resolve(
                "java/com/hellovoid/liquiddock/LauncherGlassVisibilityTransition.java"));
        String registry = Files.readString(MAIN.resolve(
                "java/com/hellovoid/liquiddock/DockGlassItemRegistry.java"));
        String hook = Files.readString(MAIN.resolve(
                "java/com/hellovoid/liquiddock/MainHook.java"));
        assertTrue(config.contains("final Animation animation;"));
        assertTrue(transition.contains("AnimationRuntimeState.workspaceVisibilityDurationMs()"));
        assertTrue(registry.contains("AnimationRuntimeState.dockIconRevealDurationMs()"));
        assertTrue(hook.contains("config.animation.dockResizeMs"));
    }
}
