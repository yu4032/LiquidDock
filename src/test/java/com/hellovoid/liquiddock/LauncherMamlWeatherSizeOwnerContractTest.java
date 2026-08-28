package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract from markdown (1).md(9) live Weather MAML element dumps. */
public class LauncherMamlWeatherSizeOwnerContractTest {
    @Test public void knownWeatherProductsUseTheirObservedSemanticBackgroundOwners()
            throws Exception {
        String suppressor = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherMamlBackgroundSuppressor.java"));

        assertTrue(suppressor.contains("b8006e83-c497-4642-9815-f674b82842b0"));
        assertTrue(suppressor.contains("c989887f-fa0d-4963-8c57-896c03e37efc"));
        assertTrue(suppressor.contains("bc0f0cd2-43fd-4323-8061-55a8bc997e1f"));
        assertTrue(suppressor.contains("\"skyColor\""));
        assertTrue(suppressor.contains("\"background\""));
        assertTrue(suppressor.contains("resolveWeatherBackgroundOwner"));

        // Keep generated payload names out of production ownership rules.
        assertFalse(suppressor.contains("sky_color_ou1b4i"));
        assertFalse(suppressor.contains("bg_1idg7e"));
        assertFalse(suppressor.contains("bg_old_1idg7e"));
        assertFalse(suppressor.contains("bg_cmor3u"));
        assertFalse(suppressor.contains("bg_old_cmor3u"));
    }
}
