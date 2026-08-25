package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract for live icon/widget/folder glass component switches. */
public class GlassComponentRuntimeToggleContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void runtimeTracksAllGlassComponentSwitches() throws Exception {
        String state = Files.readString(MAIN.resolve("GlassRuntimeState.java"));
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));

        assertTrue(state.contains("ConfigSchema.Glass.ICON_GLASS.name()"));
        assertTrue(state.contains("ConfigSchema.Glass.WIDGET_GLASS.name()"));
        assertTrue(state.contains("ConfigSchema.Glass.SMALL_FOLDER_GLASS.name()"));
        assertTrue(state.contains("ConfigSchema.Glass.LARGE_FOLDER_GLASS.name()"));
        assertTrue(state.contains("static boolean isIconEnabled()"));
        assertTrue(state.contains("static boolean isWidgetEnabled()"));
        assertTrue(state.contains("static boolean isSmallFolderEnabled()"));
        assertTrue(state.contains("static boolean isLargeFolderEnabled()"));
        assertTrue(module.contains("runtimeConfig.glass.iconEnabled"));
        assertTrue(module.contains("runtimeConfig.glass.widgetEnabled"));
        assertTrue(module.contains("runtimeConfig.glass.smallFolderStyle.enabled"));
        assertTrue(module.contains("runtimeConfig.glass.largeFolderStyle.enabled"));
    }

    @Test
    public void componentDisableTransitionsHaveDedicatedTeardownCallbacks() throws Exception {
        String state = Files.readString(MAIN.resolve("GlassRuntimeState.java"));
        assertTrue(state.contains("onRuntimeIconGlassDisabled()"));
        assertTrue(state.contains("onRuntimeWidgetGlassDisabled()"));
        assertTrue(state.contains("onRuntimeSmallFolderGlassDisabled()"));
        assertTrue(state.contains("onRuntimeLargeFolderGlassDisabled()"));
    }
}
