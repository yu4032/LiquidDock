package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Settings is an opt-in module scope, not a live UI replacement yet. */
public class SystemSettingsScopeContractTest {
    private static final Path MAIN = Path.of("src/main");

    @Test
    public void settingsScopeLoadsWithoutInstallingAnyFeatureHooks() throws Exception {
        String scope = Files.readString(
                MAIN.resolve("resources/META-INF/xposed/scope.list"));
        String module = Files.readString(
                MAIN.resolve("java/com/hellovoid/liquiddock/ModuleMain.java"));
        String thirdPartyRegistry = Files.readString(
                MAIN.resolve("java/com/hellovoid/liquiddock/ThirdPartyGlassAdapterRegistry.java"));

        assertTrue(scope.lines().anyMatch("com.android.settings"::equals));
        assertTrue(module.contains("SETTINGS_PACKAGE = \"com.android.settings\""));
        int settingsGate = module.indexOf("if (SETTINGS_PACKAGE.equals(packageName)) return;");
        int systemUiGate = module.indexOf("if (SYSTEM_UI_PACKAGE.equals(packageName))");
        assertTrue("Settings must return before all feature installers", settingsGate > 0);
        assertTrue(settingsGate < systemUiGate);
        assertFalse(thirdPartyRegistry.contains("\"com.android.settings\""));
    }
}
