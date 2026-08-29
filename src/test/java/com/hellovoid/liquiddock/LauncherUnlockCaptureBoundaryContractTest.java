package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Focused regression contract: unlock capture must not sample the lockscreen composition. */
public class LauncherUnlockCaptureBoundaryContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(String name) throws Exception {
        return Files.readString(MAIN.resolve(name));
    }

    @Test public void unlockReleaseRequiresPresentationCompletionAndUserPresent() throws Exception {
        String hook = read("LauncherGlassHomePresentationHook.java");

        assertTrue(hook.contains("hookUnlockUserPresent(classLoader)"));
        assertTrue(hook.contains("\"onUserPresent\""));
        assertTrue(hook.contains("unlockPresentationComplete"));
        assertTrue(hook.contains("unlockUserPresentObserved"));
        assertTrue(hook.contains(
                "!unlockPresentationComplete || !unlockUserPresentObserved"));
        assertTrue(hook.contains("markUnlockPresentationComplete(\"Spring/all-views-complete\")"));
        assertTrue(hook.contains("markUnlockPresentationComplete(\"IDLE/no-active-animation\")"));
        assertTrue(hook.contains("tryReleaseUnlockBarrier(\"USER_PRESENT\")"));

        int gate = hook.indexOf("private static void tryReleaseUnlockBarrier");
        int frame = hook.indexOf("scheduleUnlockBarrierReleaseAfterFrame", gate);
        int rollover = hook.indexOf("prepareUnlockCaptureReturn", frame);
        int release = hook.indexOf("setUnlockTransitionPendingForAll(false)", rollover);
        assertTrue(gate >= 0 && frame > gate && rollover > frame && release > rollover);
    }

    @Test public void workspacePassBlurStaysBlockedUntilTheCombinedUnlockBarrierReleases()
            throws Exception {
        String hook = read("LauncherGlassHomePresentationHook.java");
        String bridge = read("Miuix307PassBlurBridge.java");

        assertTrue(hook.contains("isUnlockCaptureBlocked()"));
        assertTrue(hook.contains("return unlockTransitionArmed"));
        assertTrue(bridge.contains(
                "launcherWorkspace && LauncherGlassHomePresentationHook.isUnlockCaptureBlocked()"));
        assertTrue(bridge.contains(
                "binding.launcherWorkspace && LauncherGlassHomePresentationHook.isUnlockCaptureBlocked()"));
    }
}
