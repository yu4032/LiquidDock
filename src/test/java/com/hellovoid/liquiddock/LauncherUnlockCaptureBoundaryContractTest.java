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

    @Test public void unlockProducerRolloverUsesTypedSessionLifecycle() throws Exception {
        String registry = read("LauncherGlassSessionRegistry.java");
        String session = read("LauncherGlassSession.java");

        assertTrue(session.contains("boolean suspendProducerForUnlockCapture()"));
        assertTrue(session.contains("boolean rebindProducer(Runnable rolloverComplete)"));
        assertTrue(registry.contains("session.suspendProducerForUnlockCapture()"));
        assertTrue(registry.contains("session.rebindProducer(completeOne)"));
        assertFalse(registry.contains("HookUtil.getField(session, \"binding\")"));
        assertFalse(registry.contains("HookUtil.findMethodExact("));
        assertFalse(registry.contains("renderHandler"));
        assertTrue(registry.contains("failed.set(true)"));
        assertTrue(registry.contains("main.post(completeOne)"));
    }

    @Test public void systemUiSourceUsesSemanticGoneFinishedBoundary() throws Exception {
        String source = read("SystemUiKeyguardGoneSource.java");
        String policy = read("SystemUiKeyguardGonePolicy.java");

        assertTrue(source.contains("KeyguardTransitionRepositoryImpl"));
        assertTrue(source.contains("TransitionStep"));
        assertTrue(source.contains("SystemUiKeyguardGonePolicy.isGoneTransitionAttempt(from, to)"));
        assertTrue(source.contains("SystemUiKeyguardGonePolicy.shouldPublishFinished(from, to, state)"));
        assertFalse(source.contains("\"LOCKSCREEN\".equals(from)"));
        assertTrue(policy.contains("GONE.equals(to)"));
        assertTrue(policy.contains("FINISHED.equals(transitionState)"));
        assertTrue(source.contains("sendBroadcast"));
        assertFalse(source.contains("ScreenCapture"));
        assertFalse(source.contains("captureDisplay"));
        assertFalse(source.contains("import android.view.SurfaceControl"));
        assertFalse(source.contains("SetPassBlurSurface"));
    }

    @Test public void systemUiObserverFailsOpenAfterOriginalTransitionCompletes() throws Exception {
        String source = read("SystemUiKeyguardGoneSource.java");

        int proceed = source.indexOf("Object result = chain.proceed(args);");
        int observerTry = source.indexOf("try {", proceed);
        int observe = source.indexOf("onTransitionStep(step);", observerTry);
        int observerCatch = source.indexOf("catch (Throwable error)", observe);
        int returnResult = source.indexOf("return result;", observerCatch);

        assertTrue("SystemUI original transition must complete before LiquidDock observes it",
                proceed >= 0 && observerTry > proceed);
        assertTrue("LiquidDock observation must be isolated from the SystemUI call path",
                observe > observerTry && observerCatch > observe);
        assertTrue("Observer failures must keep the original SystemUI result",
                returnResult > observerCatch);
        assertTrue(source.contains("SystemUI unlock observer failed"));
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
