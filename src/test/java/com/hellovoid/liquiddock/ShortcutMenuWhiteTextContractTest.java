package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class ShortcutMenuWhiteTextContractTest {
    @Test public void shortcutMenuWhiteTextHasDedicatedDefaultOffSettingAfterGlassToggle() throws Exception {
        String schema = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"));
        String settings = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));

        assertTrue(schema.contains("SHORTCUT_POPUP_DARK_TEXT = bool("));
        assertTrue(schema.contains("\"liquid_shortcut_popup_dark_text\", false, false, false"));
        assertTrue(schema.contains("Glass.SHORTCUT_POPUP_GLASS, Glass.SHORTCUT_POPUP_DARK_TEXT"));
        assertTrue(settings.contains("ConfigSchema.Glass.SHORTCUT_POPUP_DARK_TEXT"));
        assertTrue(settings.contains("快捷菜单深色模式文字"));
        assertTrue(settings.indexOf("ConfigSchema.Glass.SHORTCUT_POPUP_DARK_TEXT")
                > settings.indexOf("ConfigSchema.Glass.SHORTCUT_POPUP_GLASS"));
    }

    @Test public void shortcutMenuWhiteTextUsesTypedAndroidViewApisWithoutProjectSelfReflection() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/MiuixShortcutMenuGlassHook.java"));

        assertTrue(hook.contains("ConfigSchema.Glass.SHORTCUT_POPUP_DARK_TEXT"));
        assertTrue(hook.contains("TextView"));
        assertTrue(hook.contains("setTextColor(Color.WHITE)"));
        assertTrue(hook.contains("OnHierarchyChangeListener"));
        assertFalse(hook.contains("Class.forName(\"com.hellovoid.liquiddock"));
        assertFalse(hook.contains("getDeclaredField(\""));
        assertFalse(hook.contains("getDeclaredMethod(\""));
    }
}
