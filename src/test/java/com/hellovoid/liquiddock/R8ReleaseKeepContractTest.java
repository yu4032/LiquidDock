package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Release-only contract: R8 must not rewrite the Xposed timing boundary. */
public class R8ReleaseKeepContractTest {
    private static final Path KEEP = Path.of("src/main/keepRules/liquiddock.keep");
    private static final Path REFLECTION_KEEP =
            Path.of("src/main/keepRules/runtime-reflection.keep");
    private static final Path SECURITY_CENTER_AUTHORITY = Path.of(
            "src/main/java/com/hellovoid/liquiddock/SecurityCenterSourceAuthorityController.java");

    @Test public void xposedTimingBoundaryHasTargetedKeepRules() throws Exception {
        assertTrue("AGP 9.3 keepRules source-set file must exist", Files.exists(KEEP));
        String rules = Files.readString(KEEP);

        assertTrue(rules.contains("-keep class com.hellovoid.liquiddock.ModuleMain { *; }"));
        assertTrue(rules.contains("-keep class com.hellovoid.liquiddock.SystemUiKeyguardGoneSource { *; }"));
        assertTrue(rules.contains("-keep class com.hellovoid.liquiddock.SystemUiKeyguardGonePolicy { *; }"));
        assertTrue(rules.contains("-keep class com.hellovoid.liquiddock.SystemUiKeyguardGoneProtocol { *; }"));
        assertTrue(rules.contains("-keep class com.hellovoid.liquiddock.SystemUiKeyguardGoneRuntime { *; }"));
        assertTrue(rules.contains("-keep class com.hellovoid.liquiddock.SystemUiHomeTransitionSource { *; }"));
        assertTrue(rules.contains("-keep class com.hellovoid.liquiddock.SystemUiHomeTransitionProtocol { *; }"));
        assertTrue(rules.contains("-keep class com.hellovoid.liquiddock.SystemUiHomeTransitionRuntime { *; }"));
        assertTrue(rules.contains("-keep class com.hellovoid.liquiddock.SystemUiHomeTransitionTracker { *; }"));
        assertTrue(rules.contains("-keep class com.hellovoid.liquiddock.LauncherGlassHomePresentationHook { *; }"));

        assertFalse("Do not disable R8 for the whole project",
                rules.contains("-keep class com.hellovoid.liquiddock.** { *; }"));

        assertTrue("reflection keep file must exist", Files.exists(REFLECTION_KEEP));
        String reflectionRules = Files.readString(REFLECTION_KEEP);
        assertFalse(reflectionRules.contains("android.os.Handler renderHandler;"));
        assertFalse(reflectionRules.contains(
                "com.hellovoid.liquiddock.Miuix307PassBlurBridge$Binding binding;"));
        assertFalse(reflectionRules.contains("void rebindProducer();"));
        assertFalse(reflectionRules.contains("LauncherGlassSession"));
    }

    @Test public void securityCenterAuthorityUsesTypedProjectOwnedApis() throws Exception {
        assertTrue("Security Center authority controller source must exist",
                Files.exists(SECURITY_CENTER_AUTHORITY));
        String source = Files.readString(SECURITY_CENTER_AUTHORITY);

        assertFalse("Project-owned Security Center lifecycle must not use raw reflection",
                source.contains("java.lang.reflect."));
        assertFalse("Project-owned coordinator members must be called through typed APIs",
                source.contains("SecurityCenterGlassCoordinator.class"));
        assertFalse("Project-owned session members must be called through typed APIs",
                source.contains("SecurityCenterGlassSession.class"));
        assertFalse("Project-owned fields must not be resolved by name",
                source.contains("getDeclaredField("));
        assertFalse("Project-owned methods must not be resolved by name",
                source.contains("getDeclaredMethod("));
    }
}
