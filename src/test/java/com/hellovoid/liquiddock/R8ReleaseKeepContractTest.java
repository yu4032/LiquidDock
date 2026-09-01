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
        assertTrue(reflectionRules.contains("android.os.Handler renderHandler;"));
        assertTrue(reflectionRules.contains(
                "com.hellovoid.liquiddock.Miuix307PassBlurBridge$Binding binding;"));
        assertTrue(reflectionRules.contains("void rebindProducer();"));
    }
}
