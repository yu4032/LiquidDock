package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Production Security Center hooks must resolve semantic roles, never obfuscated member names. */
public class SecurityCenterNoNameAnchorContractTest {
    private static final Path MAIN = Paths.get("src/main/java/com/hellovoid/liquiddock");

    @Test public void hookSpecExposesNoNameBasedVendorAuthorities() throws Exception {
        String spec = Files.readString(MAIN.resolve("SecurityCenterHookSpec.java"));
        for (String semanticName : new String[]{
                "CONFIGURE_DOCK_METHOD",
                "DOCK_READY_METHOD",
                "TOGGLE_ALL_APPS_METHOD",
                "FINAL_BACKGROUND_METHOD",
                "REMOVE_TURBO_LAYOUT_METHOD",
                "REMOVE_TURBO_LAYOUT_WITHOUT_ANIMATION_METHOD",
                "SIDEBAR_TURBO_GETTER",
                "GAME_MATERIAL_RESTORE_METHOD",
                "VIDEO_MATERIAL_RESTORE_METHOD",
                "ALL_APPS_PRESENT_FIELD",
                "TRANSFORMING_FIELD"}) {
            assertFalse("name-based vendor authority must be removed: " + semanticName,
                    spec.contains(semanticName));
        }
    }

    @Test public void resolverAndHooksDoNotDependOnNameBasedTransitionOrTeardownRoles()
            throws Exception {
        String resolver = Files.readString(MAIN.resolve("SecurityCenterSemanticContractResolver.java"));
        String hook = Files.readString(MAIN.resolve("SecurityCenterGlassHook.java"));

        assertFalse(resolver.contains("namedPrivateWrapperBoolean("));
        assertFalse(resolver.contains("namedBooleanField("));
        assertFalse(hook.contains("contract.toggleAllApps()"));
        assertFalse(hook.contains("contract.allAppsPresent()"));
        assertFalse(hook.contains("contract.transforming()"));
        assertFalse(hook.contains("contract.removeAnimated()"));
        assertFalse(hook.contains("contract.removeWithoutAnimation()"));
    }
}
