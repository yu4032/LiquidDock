package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Static vendor hook boundary for Launcher 4.50 widget <-> app transitions. */
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
    }
}
