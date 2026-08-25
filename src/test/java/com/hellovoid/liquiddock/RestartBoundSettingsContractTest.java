package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** UI contract for settings whose hook/layout structure requires a Launcher restart. */
public class RestartBoundSettingsContractTest {
    private static final Path UI = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");

    @Test
    public void structuralBooleanSwitchesSayRestartIsRequired() throws Exception {
        String source = Files.readString(UI);

        assertTrue(source.contains("private fun restartBoundSummary(summary: String)"));
        assertTrue(source.contains("重启桌面生效"));

        assertTrue(lineContaining(source, "ConfigSchema.Grid.ENABLED,")
                .contains("restartBoundSummary("));
        assertTrue(lineContaining(source, "ConfigSchema.Grid.WIDGET_ADAPTATION,")
                .contains("restartBoundSummary("));
        assertTrue(lineContaining(source, "ConfigSchema.Dock.RESIZE_ANIMATION,")
                .contains("restartBoundSummary("));
        assertTrue(lineContaining(source, "ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION,")
                .contains("restartBoundSummary("));
        assertTrue(lineContaining(source, "ConfigSchema.Workstation.DOCK_CUSTOMIZATION,")
                .contains("restartBoundSummary("));
    }

    @Test
    public void runtimeSafeVisualSwitchesRemainImmediate() throws Exception {
        String source = Files.readString(UI);

        assertFalse(lineContaining(source, "ConfigSchema.Dock.ENABLED,")
                .contains("restartBoundSummary("));
        assertFalse(lineContaining(source, "ConfigSchema.Glass.ICON_GLASS,")
                .contains("restartBoundSummary("));
        assertFalse(lineContaining(source, "ConfigSchema.Glass.WIDGET_GLASS,")
                .contains("restartBoundSummary("));
        assertFalse(lineContaining(source, "ConfigSchema.Glass.SMALL_FOLDER_GLASS,")
                .contains("restartBoundSummary("));
        assertFalse(lineContaining(source, "ConfigSchema.Glass.LARGE_FOLDER_GLASS,")
                .contains("restartBoundSummary("));
        assertFalse(lineContaining(source, "ConfigSchema.Divider.ENABLED,")
                .contains("restartBoundSummary("));
    }

    @Test
    public void debugLoggingKeepsItsExistingRestartWording() throws Exception {
        String source = Files.readString(UI);
        String line = lineContaining(source, "ConfigSchema.Debug.LOGGING");
        assertTrue(line.contains("重启桌面生效"));
    }

    private static String lineContaining(String source, String marker) {
        int at = source.indexOf(marker);
        if (at < 0) return "";
        int start = source.lastIndexOf('\n', at);
        int end = source.indexOf('\n', at);
        if (start < 0) start = 0;
        else start += 1;
        if (end < 0) end = source.length();
        return source.substring(start, end);
    }
}
