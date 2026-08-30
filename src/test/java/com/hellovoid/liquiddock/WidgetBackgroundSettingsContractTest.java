package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class WidgetBackgroundSettingsContractTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path MAIN_KOTLIN = Path.of("src/main/kotlin/com/hellovoid/liquiddock");

    @Test public void userWidgetRulesLiveInTheNormalConfigSchema() throws Exception {
        String schema = Files.readString(MAIN_JAVA.resolve("config/ConfigSchema.java"));

        assertTrue(schema.contains("WIDGET_BACKGROUND_BUILTIN_RULES = bool("));
        assertTrue(schema.contains("\"liquid_widget_background_builtin_rules\""));
        assertTrue(schema.contains("WIDGET_BACKGROUND_USER_RULES = string("));
        assertTrue(schema.contains("\"liquid_widget_background_user_rules\""));
        assertTrue(schema.contains("Glass.WIDGET_BACKGROUND_BUILTIN_RULES"));
        assertTrue(schema.contains("Glass.WIDGET_BACKGROUND_USER_RULES"));
    }

    @Test public void liquidGlassPageExposesLiveDiscoveryControls() throws Exception {
        String settings = Files.readString(MAIN_KOTLIN.resolve("ComposeSettingsActivity.kt"));
        Path pagePath = MAIN_KOTLIN.resolve("WidgetBackgroundSettingsPage.kt");

        assertTrue(settings.contains("WidgetBackgrounds(R.string.page_widget_backgrounds)"));
        assertTrue(settings.contains("Page.LauncherHighlights, Page.WidgetBackgrounds -> Page.Liquid"));
        assertTrue(settings.contains("openWidgetBackgrounds"));
        assertTrue(settings.contains("WidgetBackgroundSettingsPage("));
        assertTrue(Files.exists(pagePath));

        String page = Files.readString(pagePath);
        assertTrue(page.contains("LiquidDockApp.widgetDiscoveryPreferences()"));
        assertTrue(page.contains("WidgetBackgroundDiscoveryCodec.decode"));
        assertTrue(page.contains("WidgetBackgroundUserRuleCodec.encode"));
        assertTrue(page.contains("SwitchPreference("));
    }
}
