package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Source-level contract for Launcher 4.50 widget <-> app glass transition wiring. */
public class LauncherWidgetTransitionWiringContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void hooksConcreteLauncher450WidgetTargetsAndClosingLookup() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));
        assertTrue(hook.contains("com.miui.home.launcher.LauncherWidgetView"));
        assertTrue(hook.contains("com.miui.home.launcher.maml.MaMlWidgetView"));
        assertTrue(hook.contains("\"setAnimTargetVisibility\""));
        assertTrue(hook.contains("com.miui.home.recents.anim.WindowAnimParamsProvider"));
        assertTrue(hook.contains("\"findClosingWidgetView\""));
        assertTrue(hook.contains("markWidgetReturnTarget"));
    }

    @Test public void widgetNodeRetainsGeometryAndWaitsForFreshBackdrop() throws Exception {
        String node = Files.readString(MAIN.resolve("LauncherGlassStaticNode.java"));
        assertTrue(node.contains("LauncherWidgetTransitionState"));
        assertTrue(node.contains("beginWidgetLaunchFadeOut"));
        assertTrue(node.contains("beginWidgetReturnWaitingFresh"));
        assertTrue(node.contains("onWidgetReturnFreshFrame"));
        assertTrue(node.contains("widgetTransitionState.shouldRetainGeometry()"));
    }

    @Test public void sceneArmsWidgetAgainstHomeGenerationAndRevealsOnlyAfterFreshFrame()
            throws Exception {
        String scene = Files.readString(MAIN.resolve("LauncherGlassSceneController.java"));
        assertTrue(scene.contains("markWidgetReturnTarget"));
        assertTrue(scene.contains("pendingWidgetReturnNode"));
        assertTrue(scene.contains("armPendingWidgetReturn"));
        assertTrue(scene.contains("onWidgetReturnFreshFrame(generation)"));
    }
}
