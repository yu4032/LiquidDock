package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class SideSlideHoldDiagnosticsContractTest {
    @Test
    public void sideSlideDiagnosticsDoNotDependOnMainDebugLogging() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Launcher450SideSlideHoldHook.java"));
        String module = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"));

        assertFalse(hook.contains("MainHook.log("));
        assertTrue(hook.contains("SideSlideHoldDiagnostics.log("));
        assertTrue(module.contains("SideSlideHoldDiagnostics.log("));
        assertTrue(module.contains("sideSlideEnabled="));
    }
}
