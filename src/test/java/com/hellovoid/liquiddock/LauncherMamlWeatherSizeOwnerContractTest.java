package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contracts from live Weather MAML element dumps. */
public class LauncherMamlWeatherSizeOwnerContractTest {
    @Test public void knownWeatherProductsUseObservedOwnersFromXmlNotJava()
            throws Exception {
        String rules = Files.readString(Path.of(
                "src/main/resources/widget_background_rules.xml"));
        String executor = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherMamlBackgroundRuleExecutor.java"));

        assertTrue(rules.contains("b8006e83-c497-4642-9815-f674b82842b0"));
        assertTrue(rules.contains("c989887f-fa0d-4963-8c57-896c03e37efc"));
        assertTrue(rules.contains("bc0f0cd2-43fd-4323-8061-55a8bc997e1f"));
        assertTrue(rules.contains("name=\"skyColor\""));
        assertTrue(rules.contains("name=\"background\""));
        assertTrue(rules.contains("appPackage=\"com.miui.weather2\""));

        // Keep generated payload names out of both built-in rules and generic executor code.
        String combined = rules + executor;
        assertFalse(combined.contains("sky_color_ou1b4i"));
        assertFalse(combined.contains("bg_1idg7e"));
        assertFalse(combined.contains("bg_old_1idg7e"));
        assertFalse(combined.contains("bg_cmor3u"));
        assertFalse(combined.contains("bg_old_cmor3u"));
        assertFalse(combined.contains("bg_3wvpbl"));
        assertFalse(combined.contains("bg_old_3wvpbl"));
    }

    @Test public void newlyObservedCompactWeatherUsesItsRegisteredBackgroundOwner()
            throws Exception {
        String rules = Files.readString(Path.of(
                "src/main/resources/widget_background_rules.xml"));

        // Device log for this exact product registers semantic parent background:ElementGroup,
        // with generated bg_3wvpbl/bg_old_3wvpbl rectangles beneath it. Own the stable parent only.
        int product = rules.indexOf("f34b4e78-9df6-467c-b0ac-e72e62aba073");
        int background = rules.indexOf("<hide-element name=\"background\"", product);
        assertTrue(product >= 0);
        assertTrue(background > product);
    }

    @Test public void unknownMamlWidgetsFallBackToDiagnosticOnlyWithoutGuessingOwner()
            throws Exception {
        String rules = Files.readString(Path.of(
                "src/main/resources/widget_background_rules.xml"));

        int fallback = rules.indexOf("id=\"maml-diagnostic\"");
        assertTrue(fallback >= 0);
        assertTrue(rules.indexOf("type=\"maml\"", fallback) > fallback);

        int nextRule = rules.indexOf("<rule", fallback + 1);
        String fallbackRule = nextRule >= 0 ? rules.substring(fallback, nextRule) : rules.substring(fallback);
        assertFalse("generic diagnostic fallback must never hide an element",
                fallbackRule.contains("hide-element"));
    }
}
