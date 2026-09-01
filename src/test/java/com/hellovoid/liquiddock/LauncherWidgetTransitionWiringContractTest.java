package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Source-level contract for Launcher 4.50 widget <-> app glass transition wiring. */
public class LauncherWidgetTransitionWiringContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void hooksConcreteLauncher450WidgetTargetsAndClosingLookup() throws Exception {
        String hook = Files.readString(MAIN.resolve("LauncherWidgetTransitionHook.java"));
        assertTrue(hook.contains("com.miui.home.launcher.LauncherWidgetView"));
        assertTrue(hook.contains("com.miui.home.launcher.maml.MaMlWidgetView"));
        assertTrue(hook.contains("\"setAnimTargetVisibility\""));
        assertTrue(hook.contains("com.miui.home.recents.anim.WindowAnimParamsProvider"));
        assertTrue(hook.contains("com.miui.home.recents.GestureModeApp"));
        assertTrue(hook.contains("com.miui.home.recents.NavStubView"));
        assertTrue(hook.contains("\"findClosingWidgetView\""));
        assertTrue(hook.contains("markWidgetReturnTarget"));
    }

    @Test public void coordinatorRemovesStaleWidgetUntilFreshHomeGeneration() throws Exception {
        String coordinator = Files.readString(MAIN.resolve("LauncherWidgetTransitionCoordinator.java"));
        assertTrue(coordinator.contains("LauncherWidgetTransitionState"));
        assertTrue(coordinator.contains("hideImmediately"));
        assertTrue(coordinator.contains("expectedFreshGeneration"));
        assertTrue(coordinator.contains("HOME_VISIBLE"));
        assertTrue(coordinator.contains("requestFreshForRoot"));
        assertTrue(coordinator.contains("setSuppressedByDrag(false)"));
    }

    @Test public void homeAuthorityArmsGenerationAndWaitsForRenderedFreshScene() throws Exception {
        String home = Files.readString(MAIN.resolve("LauncherGlassHomePresentationHook.java"));
        assertTrue(home.contains("LauncherWidgetTransitionHook.install(classLoader)"));
        assertTrue(home.contains("LauncherWidgetTransitionCoordinator.onHomeOpeningStarted()"));
        assertTrue(home.contains("LauncherWidgetTransitionCoordinator.onHomeBarrierReleased()"));
    }
}
