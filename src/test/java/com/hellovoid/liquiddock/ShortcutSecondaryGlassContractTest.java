package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static HyperOS 4.50 vendor/API boundary contract for shortcut-menu popup glass. */
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
        assertTrue(hook.contains("com.miui.home.launcher.EditStateChangeReason"));
        assertTrue(hook.contains("mDecorView"));
        assertTrue(hook.contains("mPopupView"));
        assertTrue(hook.contains("getContentView"));
        assertTrue(hook.contains("LauncherGlassSessionRegistry.acquire("));
        assertTrue(hook.contains("decorView, binding.glassConfig"));
        assertTrue(hook.contains("LauncherGlassSinkView.attachToExternalMaterial"));
        assertTrue(sink.contains("attachToExternalMaterial"));
        assertTrue(sink.contains("externalSessionAuthority"));
    }
}
