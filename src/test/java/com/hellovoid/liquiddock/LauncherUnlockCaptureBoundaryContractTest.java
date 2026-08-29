package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contracts for never publishing a lockscreen frame as HOME glass after unlock. */
public class LauncherUnlockCaptureBoundaryContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(String name) throws Exception {
        return Files.readString(MAIN.resolve(name));
    }

    @Test public void unlockBarrierReleasesOnlyAfterAnimationFrameAndEndpointRollover() throws Exception {
        String hook = read("LauncherGlassHomePresentationHook.java");
        String registry = read("LauncherGlassSessionRegistry.java");

        assertTrue(hook.contains("postFrameCallback"));
        assertTrue(hook.contains("scheduleUnlockBarrierReleaseAfterFrame"));
        assertTrue(hook.contains("prepareUnlockCaptureReturn"));
        assertTrue(registry.contains("prepareUnlockCaptureReturn"));
        assertTrue(registry.contains("HookUtil.findMethodExact"));
        assertTrue(registry.contains("rebind.invoke(session)"));
        assertTrue(registry.contains("renderHandler.post"));

        int terminal = hook.indexOf("releaseUnlockWhenSpringComplete");
        int schedule = hook.indexOf("scheduleUnlockBarrierReleaseAfterFrame", terminal);
        int rollover = hook.indexOf("prepareUnlockCaptureReturn", schedule);
        int clear = hook.indexOf("setUnlockTransitionPendingForAll(false)", rollover);
        assertTrue("unlock pending must clear only after post-animation endpoint rollover completes",
                terminal >= 0 && schedule > terminal && rollover > schedule && clear > rollover);
    }

    @Test public void unlockPreparePausesExistingPassBlurRegardlessOfWorkstationPolicy() throws Exception {
        String hook = read("LauncherGlassHomePresentationHook.java");
        String registry = read("LauncherGlassSessionRegistry.java");

        assertTrue(hook.contains("suspendForUnlockCapture"));
        assertTrue(registry.contains("suspendForUnlockCapture"));
        assertTrue(registry.contains("Miuix307PassBlurBridge.pauseUpdates"));
    }

    @Test public void passBlurGateAppliesOnlyToLauncherWorkspaceBindings() throws Exception {
        String hook = read("LauncherGlassHomePresentationHook.java");
        String bridge = read("Miuix307PassBlurBridge.java");

        assertTrue(hook.contains("isUnlockCaptureBlocked"));
        assertTrue(bridge.contains("final boolean launcherWorkspace"));
        assertTrue(bridge.contains("LauncherGlassSceneController.findRoot(materialHost) != null"));

        int bind = bridge.indexOf("static Binding bind");
        int bindScope = bridge.indexOf("boolean launcherWorkspace", bind);
        int bindGate = bridge.indexOf("launcherWorkspace && LauncherGlassHomePresentationHook.isUnlockCaptureBlocked()", bind);
        int bindTransaction = bridge.indexOf("SetPassBlurSurface", bind);
        assertTrue("only Launcher Workspace continuous-on-bind must be rejected during unlock",
                bind >= 0 && bindScope > bind && bindGate > bindScope && bindTransaction > bindGate);

        int pulse = bridge.indexOf("static void requestSingleUpdate");
        int pulseGate = bridge.indexOf("binding.launcherWorkspace && LauncherGlassHomePresentationHook.isUnlockCaptureBlocked()", pulse);
        int pulseEnable = bridge.indexOf("setUpdatesEnabled(binding, true)", pulse);
        assertTrue("Workspace single-frame refresh must be rejected during unlock",
                pulse >= 0 && pulseGate > pulse && pulseEnable > pulseGate);

        int resume = bridge.indexOf("static void resumeUpdates");
        int resumeGate = bridge.indexOf("binding.launcherWorkspace && LauncherGlassHomePresentationHook.isUnlockCaptureBlocked()", resume);
        int resumeEnable = bridge.indexOf("setUpdatesEnabled(binding, true)", resume);
        assertTrue("Workspace persistent refresh must be rejected without blocking Floating Dock",
                resume >= 0 && resumeGate > resume && resumeEnable > resumeGate);
    }

    @Test public void staticLayerRemainsHiddenUntilPostUnlockFreshGenerationArrives() throws Exception {
        String scene = read("LauncherGlassSceneController.java");

        assertTrue(scene.contains("state.onGenerationInvalidated()"));
        assertTrue(scene.contains("current.setSceneVisible(state.isLayerVisible()"));
        assertTrue(scene.contains("private void onFreshFrameReady(long generation)"));
        assertTrue(scene.contains("state.onFreshFrameReady(generation)"));
    }
}
