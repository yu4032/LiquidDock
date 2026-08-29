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

    @Test public void unlockBarrierReleasesOnAFrameAfterVendorAnimationCompletes() throws Exception {
        String hook = read("LauncherGlassHomePresentationHook.java");

        assertTrue(hook.contains("postFrameCallback"));
        assertTrue(hook.contains("scheduleUnlockBarrierReleaseAfterFrame"));
        int terminal = hook.indexOf("releaseUnlockWhenSpringComplete");
        int schedule = hook.indexOf("scheduleUnlockBarrierReleaseAfterFrame", terminal);
        int clear = hook.indexOf("setUnlockTransitionPendingForAll(false)", schedule);
        assertTrue("unlock capture gate must remain closed until a later Choreographer frame",
                terminal >= 0 && schedule > terminal && clear > schedule);
    }

    @Test public void unlockSettlingRollsTheProducerEndpointBeforeRequestingHomeFrame() throws Exception {
        String scene = read("LauncherGlassSceneController.java");
        String session = read("LauncherGlassSession.java");

        assertTrue(scene.contains("unlockCaptureNeedsFreshEndpoint"));
        assertTrue(scene.contains("requestFreshBackdropAfterUnlock"));
        assertTrue(session.contains("void requestFreshBackdropAfterUnlock(long generation)"));

        int method = session.indexOf("void requestFreshBackdropAfterUnlock(long generation)");
        int invalidate = session.indexOf("invalidateGeneration(generation)", method);
        int roll = session.indexOf("rebindProducer()", invalidate);
        assertTrue("old SurfaceTexture/PassBlur endpoint must be discarded before post-unlock capture",
                method >= 0 && invalidate > method && roll > invalidate);
    }

    @Test public void passBlurCannotBindOrRefreshWhilePresentationCaptureIsBlocked() throws Exception {
        String scene = read("LauncherGlassSceneController.java");
        String session = read("LauncherGlassSession.java");

        assertTrue(scene.contains("isCaptureBlockedForRoot"));

        int bindReady = session.indexOf("private void bindProducerWhenReady");
        int bindGate = session.indexOf("LauncherGlassSceneController.isCaptureBlockedForRoot(root)", bindReady);
        int vendorBind = session.indexOf("Miuix307PassBlurBridge.bind", bindReady);
        assertTrue("default continuous-on-bind must never run during unlock presentation",
                bindReady >= 0 && bindGate > bindReady && vendorBind > bindGate);

        int refresh = session.indexOf("private void requestProducerRefresh");
        int refreshGate = session.indexOf("LauncherGlassSceneController.isCaptureBlockedForRoot(root)", refresh);
        int pulse = session.indexOf("Miuix307PassBlurBridge.requestSingleUpdate", refresh);
        assertTrue("no existing PassBlur endpoint may pulse while unlock presentation is pending",
                refresh >= 0 && refreshGate > refresh && pulse > refreshGate);
    }

    @Test public void staticLayerRemainsHiddenUntilPostUnlockFreshGenerationArrives() throws Exception {
        String scene = read("LauncherGlassSceneController.java");

        assertTrue(scene.contains("state.onGenerationInvalidated()"));
        assertTrue(scene.contains("current.setSceneVisible(state.isLayerVisible()"));
        assertTrue(scene.contains("private void onFreshFrameReady(long generation)"));
        assertTrue(scene.contains("state.onFreshFrameReady(generation)"));
    }
}
