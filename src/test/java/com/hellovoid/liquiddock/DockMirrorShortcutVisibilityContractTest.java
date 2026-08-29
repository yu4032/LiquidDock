package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Keeps the phone-mirroring feature alive while removing only its Dock entry. */
public class DockMirrorShortcutVisibilityContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path CONFIG = MAIN.resolve("config/ConfigSchema.java");
    private static final Path COMPOSE = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");

    @Test
    public void dockMirrorShortcutCanBeHiddenLiveWithoutChangingSecureSettings() throws Exception {
        String config = Files.readString(CONFIG);
        String runtime = Files.readString(MAIN.resolve("VisualRuntimeState.java"));
        String entry = Files.readString(MAIN.resolve("ModuleMain.java"));
        String compose = Files.readString(COMPOSE);
        Path hookPath = MAIN.resolve("DockMirrorShortcutHook.java");

        assertTrue(config.contains("HIDE_MIRROR_SHORTCUT"));
        assertTrue(config.contains("\"dock_hide_mirror_shortcut\""));
        assertTrue(Files.exists(hookPath));

        String hook = Files.readString(hookPath);
        assertTrue(hook.contains("com.xiaomi.mirror.SystemSettingsUtils"));
        assertTrue(hook.contains("pref_key_mirror_switch"));
        assertTrue(hook.contains("VisualRuntimeState.isMirrorShortcutHidden()"));
        assertTrue(hook.contains("com.miui.home.launcher.hotseats.HotSeatsList"));
        assertTrue(hook.contains("onMirrorSeatUpdate"));
        assertFalse(hook.contains("Settings.Secure"));
        assertFalse(hook.contains("putInt("));
        assertFalse(hook.contains("setVisibility("));

        assertTrue(runtime.contains("ConfigSchema.Dock.HIDE_MIRROR_SHORTCUT"));
        assertTrue(runtime.contains("DockMirrorShortcutHook.onRuntimeVisibilityChanged()"));
        assertTrue(entry.contains("DockMirrorShortcutHook.install(classLoader)"));
        assertTrue(compose.contains(
                "BooleanSetting(prefs, ConfigSchema.Dock.HIDE_MIRROR_SHORTCUT"));
        assertTrue(compose.contains("R.string.dock_hide_mirror_shortcut"));
        assertTrue(compose.contains("R.string.dock_hide_mirror_shortcut_summary"));
    }
}
