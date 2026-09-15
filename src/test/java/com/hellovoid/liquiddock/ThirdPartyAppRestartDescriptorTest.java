package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static architecture contracts that keep third-party app restart UI extensible. */
public class ThirdPartyAppRestartDescriptorTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path COMPOSE_SETTINGS = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");

    private static String read(Path path) throws Exception {
        return Files.exists(path) ? Files.readString(path) : "";
    }

    @Test public void thirdPartyAppPagesUseCentralRestartDescriptors() throws Exception {
        String compose = read(COMPOSE_SETTINGS);

        assertTrue(compose.contains("ThirdPartyAppPageDescriptor"));
        assertTrue(compose.contains("THIRD_PARTY_APP_PAGES"));
        assertTrue(compose.contains("Page.Gboard to ThirdPartyAppPageDescriptor"));
        assertTrue(compose.contains("com.google.android.inputmethod.latin"));
        assertTrue(compose.contains("descriptor.restartLabelRes"));
        assertTrue(compose.contains("activity.restartPackageProcess("));
        assertFalse(compose.contains("if (page == Page.Gboard)"));
        assertFalse(compose.contains("activity.restartGboard()"));
    }

    @Test public void packageProcessRestartIsGenericAndKeepsImeSemantics() throws Exception {
        String activity = read(MAIN.resolve("SettingsActivity.java"));

        assertTrue(activity.contains("void restartPackageProcess(String packageName, String displayName)"));
        assertTrue(activity.contains("pidof \" + packageName"));
        assertTrue(activity.contains("kill -TERM $PIDS"));
        assertFalse(activity.contains("void restartGboard()"));
        assertFalse(activity.contains("am force-stop com.google.android.inputmethod.latin"));
    }
}
