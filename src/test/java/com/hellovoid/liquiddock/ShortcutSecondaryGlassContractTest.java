package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contract for the HyperOS Launcher shortcut-menu popup glass bridge. */
public class ShortcutSecondaryGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void shortcutMenuPopupUsesLauncherProducerAndExternalSink() throws Exception {
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        String sink = Files.readString(MAIN.resolve("LauncherGlassSinkView.java"));
        String hook = Files.readString(MAIN.resolve("MiuixShortcutMenuGlassHook.java"));

        assertTrue(module.contains("MiuixShortcutMenuGlassHook.install(classLoader, runtimeConfig)"));
        assertTrue(hook.contains("com.miui.home.launcher.shortcuts.ShortcutMenu"));
        assertTrue(hook.contains("\"show\""));
        assertTrue(hook.contains("\"dismiss\""));
        assertTrue(hook.contains("mDecorView"));
        assertTrue(hook.contains("mPopupView"));
        assertTrue(hook.contains("getContentView"));
        assertTrue(hook.contains("LauncherGlassSessionRegistry.acquire(decorView, glassConfig)"));
        assertTrue(hook.contains("LauncherGlassSinkView.attachToExternalMaterial"));
        assertTrue(sink.contains("attachToExternalMaterial"));
        assertTrue(sink.contains("LauncherGlassSession shared"));
    }

    @Test public void stockPopupMaterialIsClearedOnlyAfterSinkExists() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixShortcutMenuGlassHook.java"));
        int sink = hook.indexOf("attachToExternalMaterial");
        int guard = hook.indexOf("if (glassSink == null)");
        int clear = hook.indexOf("clearVendorPopupMaterial");
        assertTrue(sink >= 0);
        assertTrue(guard > sink);
        assertTrue(clear > guard);
    }
}
