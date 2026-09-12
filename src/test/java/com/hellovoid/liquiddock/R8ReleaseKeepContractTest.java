package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Release-only contract: R8 must not rewrite runtime-by-name boundaries. */
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
        assertFalse("Removed screenshot-era classes must not retain stale keep rules",
                rules.contains("LiveScreenCapture"));

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

    @Test public void securityCenterSelfReflectionHasTargetedReleaseKeeps() throws Exception {
        assertTrue("Security Center authority controller source must exist",
                Files.exists(SECURITY_CENTER_AUTHORITY));
        assertTrue("reflection keep file must exist", Files.exists(REFLECTION_KEEP));

        String source = Files.readString(SECURITY_CENTER_AUTHORITY);
        String rules = Files.readString(REFLECTION_KEEP);

        assertTrue("The intentional self-reflection exception must remain explicit",
                source.contains("java.lang.reflect.Field")
                        && source.contains("java.lang.reflect.Method")
                        && source.contains("getDeclaredField(")
                        && source.contains("getDeclaredMethod("));

        assertTrue(rules.contains(
                "-keepclassmembers class com.hellovoid.liquiddock.SecurityCenterGlassCoordinator"));
        assertTrue(rules.contains("com.hellovoid.liquiddock.SecurityCenterGlassSceneState scene;"));
        assertTrue(rules.contains(
                "com.hellovoid.liquiddock.SecurityCenterGlassSceneState$Target targetKind;"));
        assertTrue(rules.contains(
                "com.hellovoid.liquiddock.SecurityCenterGlassFrameGeometry currentFrame;"));
        assertTrue(rules.contains("com.hellovoid.liquiddock.SecurityCenterGlassSession session;"));
        assertTrue(rules.contains("java.lang.ref.WeakReference turboRef;"));
        assertTrue(rules.contains("void reconcileSinks();"));
        assertTrue(rules.contains("boolean syncSinksFromMaterials();"));
        assertTrue(rules.contains(
                "com.hellovoid.liquiddock.SecurityCenterGlassFrameGeometry captureFrame(boolean);"));
        assertTrue(rules.contains("void hideAndRestoreVendor();"));
        assertTrue(rules.contains("void prepareCustomOwnershipForPresentation();"));
        assertTrue(rules.contains(
                "void requestCurrentGeneration(com.hellovoid.liquiddock.SecurityCenterGlassFrameGeometry);"));

        assertTrue(rules.contains(
                "-keepclassmembers class com.hellovoid.liquiddock.SecurityCenterGlassSession"));
        assertTrue(rules.contains("com.hellovoid.liquiddock.RootPassBlurBackend sourceBackend;"));

        assertFalse("Keep only reflected members, not the whole coordinator",
                rules.contains(
                        "-keep class com.hellovoid.liquiddock.SecurityCenterGlassCoordinator { *; }"));
        assertFalse("Keep only reflected members, not the whole session",
                rules.contains(
                        "-keep class com.hellovoid.liquiddock.SecurityCenterGlassSession { *; }"));
        assertFalse("Never keep the whole LiquidDock package",
                rules.contains("-keep class com.hellovoid.liquiddock.** { *; }"));
    }
}
