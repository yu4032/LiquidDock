package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.junit.Test;

/** UI contract for settings whose hook/layout structure requires a Launcher restart. */
public class RestartBoundSettingsContractTest {
    private static final Path UI = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");
    private static final Path EN = Path.of("src/main/res/values/strings.xml");
    private static final Path ZH = Path.of("src/main/res/values-zh-rCN/strings.xml");

    @Test
    public void structuralBooleanSwitchesSayRestartIsRequired() throws Exception {
        String en = Files.readString(EN);
        String zh = Files.readString(ZH);
        String[] summaries = {
                "enable_liquiddock_summary",
                "enable_grid_8x4_summary",
                "enable_widget_adaptation_summary",
                "dock_resize_animation_summary",
                "dock_smooth_resize_animation_summary",
                "workstation_customization_summary"
        };

        for (String name : summaries) {
            assertTrue(name + " must explain launcher restart in English",
                    stringValue(en, name).toLowerCase(Locale.ROOT).contains("restart"));
            assertTrue(name + " must explain launcher restart in Chinese",
                    stringValue(zh, name).contains("重启桌面生效"));
        }
    }

    @Test
    public void glassPageExplainsLiveDisableWithoutPromisingFullHotReload() throws Exception {
        String en = Files.readString(EN);
        String zh = Files.readString(ZH);

        String enMaster = stringValue(en, "liquid_enable_summary").toLowerCase(Locale.ROOT);
        String zhMaster = stringValue(zh, "liquid_enable_summary");
        assertTrue(enMaster.contains("turning off") || enMaster.contains("disable"));
        assertTrue(enMaster.contains("restart"));
        assertTrue(zhMaster.contains("关闭"));
        assertTrue(zhMaster.contains("重启"));

        String enHeader = stringValue(en, "liquid_header_summary").toLowerCase(Locale.ROOT);
        String zhHeader = stringValue(zh, "liquid_header_summary");
        assertTrue(enHeader.contains("component") && enHeader.contains("restart"));
        assertTrue(zhHeader.contains("组件") && zhHeader.contains("重启"));
    }

    @Test
    public void runtimeSafeComponentDisableSwitchesDoNotClaimRestartIsRequired() throws Exception {
        String source = Files.readString(UI);
        String en = Files.readString(EN);
        String zh = Files.readString(ZH);

        assertFalse(stringValue(en, "dock_customization_summary")
                .toLowerCase(Locale.ROOT).contains("restart"));
        assertFalse(stringValue(zh, "dock_customization_summary").contains("重启"));

        assertFalse(lineContaining(source, "ConfigSchema.Glass.ICON_GLASS,").contains("重启"));
        assertFalse(lineContaining(source, "ConfigSchema.Glass.WIDGET_GLASS,").contains("重启"));
        assertFalse(lineContaining(source, "ConfigSchema.Glass.SMALL_FOLDER_GLASS,").contains("重启"));
        assertFalse(lineContaining(source, "ConfigSchema.Glass.LARGE_FOLDER_GLASS,").contains("重启"));
        assertFalse(lineContaining(source, "ConfigSchema.Divider.ENABLED,").contains("重启"));
    }

    @Test
    public void debugLoggingKeepsItsExistingRestartWording() throws Exception {
        String source = Files.readString(UI);
        String line = lineContaining(source, "ConfigSchema.Debug.LOGGING");
        assertTrue(line.contains("重启桌面生效"));
    }

    @Test
    public void systemUiRestartIsOnlyRenderedOnHomePage() throws Exception {
        String source = Files.readString(UI);
        int actionsAt = source.indexOf("actions = {");
        int launcherRestartAt = source.indexOf("action_restart_launcher", actionsAt);
        int systemUiRestartAt = source.indexOf("action_restart_system_ui", actionsAt);
        int homeGuardAt = source.lastIndexOf("if (page == Page.Home)", systemUiRestartAt);

        assertTrue("top app bar actions must exist", actionsAt >= 0);
        assertTrue("launcher restart must remain visible on every page", launcherRestartAt > actionsAt);
        assertTrue("SystemUI restart must still exist", systemUiRestartAt > launcherRestartAt);
        assertTrue("SystemUI restart must be guarded by the Home page",
                homeGuardAt > launcherRestartAt && homeGuardAt < systemUiRestartAt);
        assertTrue("Home-page guard must directly wrap the SystemUI action",
                systemUiRestartAt - homeGuardAt < 300);
    }

    private static String stringValue(String xml, String name) {
        String startMarker = "<string name=\"" + name + "\">";
        int start = xml.indexOf(startMarker);
        if (start < 0) return "";
        start += startMarker.length();
        int end = xml.indexOf("</string>", start);
        if (end < 0) return "";
        return xml.substring(start, end);
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
