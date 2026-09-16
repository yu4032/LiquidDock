package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class MiuiSearchboxBackdropOwnershipContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void searchboxDisablesBackdropBinderThroughStableSemanticApi() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuiSearchboxGlassHook.java"));
        String material = Files.readString(MAIN.resolve("MiuiSearchboxVendorMaterial.java"));

        assertTrue(hook.contains("backgroundClass.getMethod(\"setBlurEnabled\", Boolean.TYPE)"));
        assertTrue(hook.contains("MiuiSearchboxVendorMaterial.release(background, blurEnabledMethod)"));
        assertTrue(hook.contains("MiuiSearchboxVendorMaterial.restore(background, blurEnabledMethod)"));

        assertTrue(material.contains("Method blurEnabledMethod"));
        assertTrue(material.contains("blurEnabledMethod.invoke(background, Boolean.FALSE)"));
        assertTrue(material.contains("blurEnabledMethod.invoke(background, Boolean.TRUE)"));

        // The helper must not rediscover vendor APIs from the runtime class.
        assertFalse(material.contains("background.getClass().getMethod("));
        assertFalse(material.contains("getDeclaredFields("));
        assertFalse(material.contains("getDeclaredConstructors("));
    }
}
