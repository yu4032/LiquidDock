package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Locks the Security Center normal/advanced material selection and fail-closed handoff. */
public class SecurityCenterAdvancedMaterialContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void advancedMaterialUsesExistingCapabilityWithoutTransparentFallback() throws Exception {
        String policy = Files.readString(MAIN.resolve("SecurityCenterMaterialModePolicy.java"));
        String hook = Files.readString(MAIN.resolve("SecurityCenterAdvancedMaterialHook.java"));
        String early = Files.readString(MAIN.resolve("SecurityCenterEarlyPrepareHook.java"));

        assertTrue(policy.contains("LiquidBlurMode.fromPersisted"));
        assertTrue(policy.contains("MiBlurBridge.isAvailable()"));
        assertTrue(policy.contains("MiBlurBridge.applyContentBlur"));
        assertTrue(hook.contains("Miuix307PrismalAdapter.toPortable("));
        assertTrue(hook.contains("(Miuix307PrismalMaterial.Params) arg, false"));
        assertTrue(hook.contains("SecurityCenterMaterialModePolicy.prepareBind(owner)"));
        assertTrue(hook.contains("SecurityCenterMaterialModePolicy.configureSink"));
        assertTrue(hook.contains("SecurityCenterMaterialModePolicy.blockCustomPresentation()"));
        assertTrue(hook.contains("SecurityCenterMaterialModePolicy.releaseSink"));
        assertTrue(hook.contains("SecurityCenterMaterialModePolicy.resetLifecycle()"));
        assertTrue(early.contains("SecurityCenterAdvancedMaterialHook.install()"));
    }
}
