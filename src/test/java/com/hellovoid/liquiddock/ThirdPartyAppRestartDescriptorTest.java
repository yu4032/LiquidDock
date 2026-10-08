package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static contracts for third-party scopes in the global restart selector. */
public class ThirdPartyAppRestartDescriptorTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path COMPOSE_SETTINGS = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");

    @Test public void thirdPartyHookScopesLiveInGlobalRestartSelector() throws Exception {
        String compose = Files.readString(COMPOSE_SETTINGS);

        assertTrue(compose.contains("com.google.android.inputmethod.latin"));
        assertTrue(compose.contains("com.android.quicksearchbox"));
        assertTrue(compose.contains("RestartScopesDialog("));
        assertFalse(compose.contains("ThirdPartyAppPageDescriptor"));
        assertFalse(compose.contains("THIRD_PARTY_APP_PAGES"));
    }

    @Test public void genericSinglePackageRestartRemainsAvailableForSecondaryActivities() throws Exception {
        String activity = Files.readString(MAIN.resolve("SettingsActivity.java"));

        assertTrue(activity.contains("void restartPackageProcess(String packageName, String displayName)"));
        assertTrue(activity.contains("pidof \" + packageName"));
        assertTrue(activity.contains("kill -TERM $PIDS"));
        assertFalse(activity.contains("void restartGboard()"));
    }
}
