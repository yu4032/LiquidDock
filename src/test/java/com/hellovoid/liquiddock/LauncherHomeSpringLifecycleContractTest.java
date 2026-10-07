package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static contract for the Launcher-owned HOME spring lifecycle. */
public class LauncherHomeSpringLifecycleContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void homeBarrierUsesWindowElementSpringCallbacks() throws Exception {
        String source = Files.readString(MAIN.resolve("LauncherGlassHomePresentationHook.java"));

        assertTrue(source.contains("\"addListener\""));
        assertTrue(source.contains("\"addAnimatorListener\""));
        assertTrue(source.contains("\"onAnimationStart\""));
        assertTrue(source.contains("\"onAnimationEnd\""));
        assertTrue(source.contains("\"onAnimationCancel\""));
        assertTrue(source.contains("\"runningAnimUpdate\""));
        assertTrue(source.contains("\"finishTransition\""));
        assertTrue(source.contains("\"onFinishCompleted\""));
        assertTrue(source.contains("\"getLastAminType\""));
        assertTrue(source.contains("\"getAnimType\""));
        assertTrue(source.contains("CLOSE_TO_HOME"));
        assertTrue(source.contains("CLOSE_TO_HOME_CENTER"));
        assertTrue(source.contains("ACTIVE_FRAME_SYNC_SPRINGS"));
        assertTrue(source.contains("onLauncherNativeTransitionStarted"));
        assertTrue(source.contains("onLauncherNativeTransitionFinished"));
        assertTrue(source.contains("releaseNativeTransitionsForOwner"));
        assertTrue(source.contains("setNativeTransitionFrameSyncForAll(true"));
        assertTrue(source.contains("setNativeTransitionFrameSyncForAll(false"));

        assertFalse(source.contains(
                "hookMethod(classLoader, WINDOW_ELEMENT, \"animTo\""));
        assertFalse(source.contains("resolveRectSpringListenerRegistration"));
        assertFalse(source.contains("SystemUiHomeTransition"));
        assertFalse(source.contains("waitForSystemUi"));
        assertFalse(source.contains("unique RectFSpringAnim listener registration method unavailable"));
    }

    @Test public void homeTimingNoLongerCrossesSystemUiProcess() throws Exception {
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        String scene = Files.readString(MAIN.resolve("LauncherGlassSceneController.java"));

        assertFalse(module.contains("SystemUiHomeTransitionSource.install"));
        assertFalse(module.contains("SystemUiHomeTransitionRuntime.install"));
        assertFalse(scene.contains("SystemUiHomeTransitionRuntime.ensureRegistered"));
    }
}
