package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class WorkspaceDropRuleHookContractTest {
    private static String read(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + name));
    }

    @Test public void customGridUsesSelectiveDropLegalityInsteadOfUnconditionalBypass()
            throws Exception {
        String entry = read("ModuleMain.java");
        String hook = read("WorkspaceDropRuleHook.java");

        assertTrue(entry.contains(
                "WorkspaceDropRuleHook.install(classLoader, customGridEnabled)"));
        assertTrue(hook.contains("LayoutDropRuleForSwapPlaces"));
        assertTrue(hook.contains("\"isLegalXY\""));
        assertTrue(hook.contains("int.class, int.class, int.class, int.class"));
        assertFalse(hook.contains("HomeGridProfile selectedProfile"));
        assertTrue(hook.contains("HookUtil.tryInvokeStatic(deviceConfig, \"getCellCountX\")"));
        assertTrue(hook.contains("HookUtil.tryInvokeStatic(deviceConfig, \"getCellCountY\")"));
        assertTrue(hook.contains("columnsResult.succeeded()"));
        assertTrue(hook.contains("rowsResult.succeeded()"));
        assertTrue(hook.contains("HomeGridDropLegalityPolicy.isLegal"));
        assertTrue(hook.contains("chain.getArg(0)"));
        assertTrue(hook.contains("chain.getArg(1)"));
        assertTrue(hook.contains("chain.getArg(2)"));
        assertTrue(hook.contains("chain.getArg(3)"));
        assertTrue(hook.contains("return chain.proceed();"));
        assertFalse(hook.contains("selectedProfile.matchesCounts"));
        assertFalse(hook.contains("return true;"));
    }
}
