package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class WidgetThemeRuntimeWiringTest {
    @Test public void launcherHostRemoteViewsUseLocalConfigurationContext() throws Exception {
        Path hookPath = Path.of("src/main/java/com/hellovoid/liquiddock/WidgetThemeHook.java");
        assertTrue("WidgetThemeHook must exist", Files.exists(hookPath));
        String hook = Files.readString(hookPath);

        assertTrue(hook.contains("com.miui.home.launcher.LauncherAppWidgetHostView"));
        assertTrue(hook.contains("RemoteViews.class.getDeclaredMethods()"));
        assertTrue(hook.contains("createConfigurationContext"));
        assertTrue(hook.contains("WidgetThemePolicy.applyToUiMode"));
        assertTrue(hook.contains("Widget theme method hook skipped"));
    }

    @Test public void moduleMainInstallsWidgetThemeHookFromPersistedMode() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"));

        assertTrue(source.contains("if (runtimeConfig.enabled) WidgetThemeHook.install"));
        assertTrue(source.contains("widget_theme_mode"));
    }
}
