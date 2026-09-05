package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static architecture/API bans for the declarative MAML background integration. */
public class LauncherMamlBackgroundRuleExecutorContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void mamlSuppressionRemainsGenericAndDataDriven() throws Exception {
        Path executorPath = MAIN.resolve("LauncherMamlBackgroundRuleExecutor.java");
        assertTrue(Files.exists(executorPath));
        String executor = Files.readString(executorPath);
        String allJava = Files.readString(MAIN.resolve("LauncherGlassVendorMaterialSuppressor.java"))
                + Files.readString(MAIN.resolve("LauncherMamlRootLoadedHook.java"))
                + executor;

        assertTrue(executor.contains("WidgetBackgroundRuleEngine.loadBundled()"));
        assertTrue(executor.contains("WidgetBackgroundIdentity"));
        assertTrue(executor.contains("rule.elementNames()"));
        assertTrue(executor.contains("dumpNamedElementsOnce"));

        assertFalse(allJava.contains("b8006e83-c497-4642-9815-f674b82842b0"));
        assertFalse(allJava.contains("c989887f-fa0d-4963-8c57-896c03e37efc"));
        assertFalse(allJava.contains("bc0f0cd2-43fd-4323-8061-55a8bc997e1f"));
        assertFalse(allJava.contains("com.miui.weather2"));
    }

    @Test public void diagnosticPathAvoidsDestructiveVendorApis() throws Exception {
        String executor = Files.readString(
                MAIN.resolve("LauncherMamlBackgroundRuleExecutor.java"));

        assertFalse(executor.contains("acceptVisitor("));
        assertFalse(executor.contains("removeElement"));
        assertFalse(executor.contains("setVisibility"));
        assertFalse(executor.contains("setColorFilter"));
    }

    @Test public void weatherSpecificSuppressorIsRemoved() {
        assertFalse(Files.exists(MAIN.resolve("LauncherMamlBackgroundSuppressor.java")));
    }
}
