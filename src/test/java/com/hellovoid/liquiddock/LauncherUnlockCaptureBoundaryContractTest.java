package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Focused regression contract: unlock capture must not sample the lockscreen wallpaper. */
public class LauncherUnlockCaptureBoundaryContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path SCOPE =
            Path.of("src/main/resources/META-INF/xposed/scope.list");

    private static String read(String name) throws Exception {
        return Files.readString(MAIN.resolve(name));
    }

    @Test public void systemUiFinishedIsTheOnlyUnlockReleaseBoundary() throws Exception {
        String hook = read("LauncherGlassHomePresentationHook.java");

        assertTrue(hook.contains("onSystemUiLockscreenGoneFinished()"));
        assertTrue(hook.contains("LauncherGlassSessionRegistry.prepareUnlockCaptureReturn"));
        assertFalse(hook.contains("hookUnlockUserPresent"));
        assertFalse(hook.contains("\"onUserPresent\""));
        assertFalse(hook.contains("unlockPresentationComplete"));
        assertFalse(hook.contains("unlockUserPresentObserved"));
        assertFalse(hook.contains("hookUnlockSpringFinish"));
        assertFalse(hook.contains("hookUnlockFolmeFinish"));
        assertFalse(hook.contains("Choreographer"));

        int boundary = hook.indexOf("static void onSystemUiLockscreenGoneFinished()");
        int rollover = hook.indexOf("LauncherGlassSessionRegistry.prepareUnlockCaptureReturn", boundary);
        int release = hook.indexOf("finishUnlockBarrierNow", rollover);
        assertTrue(boundary >= 0 && rollover > boundary && release > rollover);
    }

    @Test public void workspacePassBlurStaysPausedBeforeSystemUiFinished() throws Exception {
        String hook = read("LauncherGlassHomePresentationHook.java");
        String bridge = read("Miuix307PassBlurBridge.java");

        assertTrue(hook.contains("PREPARE.equals(state)"));
        assertTrue(hook.contains("setUnlockTransitionPendingForAll(true)"));
        assertTrue(hook.contains("LauncherGlassSessionRegistry.suspendForUnlockCapture()"));
        assertTrue(hook.contains("isUnlockCaptureBlocked()"));
        assertTrue(hook.contains("return unlockTransitionArmed"));
        assertTrue(bridge.contains(
                "launcherWorkspace && LauncherGlassHomePresentationHook.isUnlockCaptureBlocked()"));
        assertTrue(bridge.contains(
                "binding.launcherWorkspace && LauncherGlassHomePresentationHook.isUnlockCaptureBlocked()"));
    }

    @Test public void systemUiSourceMatchesExactKeyguardTransitionOnly() throws Exception {
        String source = read("SystemUiKeyguardGoneSource.java");

        assertTrue(source.contains("KeyguardTransitionRepositoryImpl"));
        assertTrue(source.contains("TransitionStep"));
        assertTrue(source.contains("\"LOCKSCREEN\""));
        assertTrue(source.contains("\"GONE\""));
        assertTrue(source.contains("\"FINISHED\""));
        assertTrue(source.contains("sendBroadcast"));
        assertFalse(source.contains("ScreenCapture"));
        assertFalse(source.contains("captureDisplay"));
        assertFalse(source.contains("import android.view.SurfaceControl"));
        assertFalse(source.contains("SetPassBlurSurface"));
    }

    @Test public void systemUiIsScopedAsObserverOnly() throws Exception {
        String module = read("ModuleMain.java");
        String scope = Files.readString(SCOPE);

        assertTrue(module.contains("SYSTEM_UI_PACKAGE"));
        assertTrue(module.contains("SystemUiKeyguardGoneSource.install"));
        assertTrue(scope.contains("com.miui.home"));
        assertTrue(scope.contains("com.android.systemui"));

        int systemUiBranch = module.indexOf("SYSTEM_UI_PACKAGE.equals(packageName)");
        int sourceInstall = module.indexOf("SystemUiKeyguardGoneSource.install", systemUiBranch);
        int earlyReturn = module.indexOf("return;", sourceInstall);
        int launcherMigration = module.indexOf("LegacyConfigMigration.migrateAtProcessStart()", earlyReturn);
        assertTrue(systemUiBranch >= 0 && sourceInstall > systemUiBranch
                && earlyReturn > sourceInstall && launcherMigration > earlyReturn);
    }
}
