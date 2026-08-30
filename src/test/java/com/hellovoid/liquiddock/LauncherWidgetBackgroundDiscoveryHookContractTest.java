package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Discovery must be tied to real widget lifecycle boundaries, not only glass-node attachment. */
public class LauncherWidgetBackgroundDiscoveryHookContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void remoteViewsUpdatePublishesAfterLauncherBuildsTheLiveChildTree() throws Exception {
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        String hook = Files.readString(MAIN.resolve("LauncherRemoteViewsWidgetUpdateHook.java"));
        String executor = Files.readString(
                MAIN.resolve("LauncherRemoteViewsBackgroundRuleExecutor.java"));

        assertTrue(module.contains("LauncherRemoteViewsWidgetUpdateHook.install(classLoader)"));
        assertTrue(hook.contains("LauncherAppWidgetHostView"));
        assertTrue(hook.contains("updateAppWidget"));
        assertTrue(hook.contains("RemoteViews.class"));
        assertTrue(hook.contains("host.post"));
        assertTrue(executor.contains("static void discover(View host)"));
    }

    @Test public void mamlRootPublishesEvenBeforeWidgetGlassIsEnabled() throws Exception {
        String hook = Files.readString(MAIN.resolve("LauncherMamlRootLoadedHook.java"));
        String executor = Files.readString(MAIN.resolve("LauncherMamlBackgroundRuleExecutor.java"));

        assertTrue(hook.contains("LauncherMamlBackgroundRuleExecutor.discoverLoadedRoot"));
        assertTrue(executor.contains("static void discoverLoadedRoot(View host, Object root)"));
    }
}
