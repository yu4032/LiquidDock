package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contracts for the decompiled HyperOS 4.50 wallpaper lifecycle bridge. */
public class LauncherWallpaperFreshnessHookContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path HOOK = MAIN.resolve("LauncherWallpaperFreshnessHook.java");

    private static String hook() throws Exception {
        assertTrue("missing LauncherWallpaperFreshnessHook", Files.exists(HOOK));
        return Files.readString(HOOK);
    }

    private static String pipeline() throws Exception {
        return Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));
    }

    @Test public void usesExactHyperOs450VendorWallpaperBoundaries() throws Exception {
        String source = hook();

        assertTrue(source.contains(
                "com.miui.home.launcher.wallpaper.DesktopWallpaperManager$MiuiWallpaperManagerCallbackStub"));
        assertTrue(source.contains("com.miui.home.launcher.Workspace"));
        assertTrue(source.contains("onWallpaperChanged"));
        assertTrue(source.contains("onWallpaperFirstFrameRendered"));
        assertTrue(source.contains("onDrawFrameEnd"));
        assertTrue(source.contains("onWallpaperColorChanged"));
    }

    @Test public void routesChangeCandidateAndAuthoritativeEventsToSceneOwner() throws Exception {
        String source = hook();

        assertTrue(source.contains("LauncherGlassSceneController"));
        assertTrue(source.contains("onWallpaperChangedForAll"));
        assertTrue(source.contains("onWallpaperCandidate"));
        assertTrue(source.contains("onWallpaperAuthoritativeForAll"));
    }

    @Test public void binderCallbacksMarshalToMainWithoutPollingOrDelay() throws Exception {
        String source = hook();

        assertTrue(source.contains("dispatchToMain"));
        assertTrue(source.contains("Looper.getMainLooper()"));
        assertTrue(source.contains("Handler"));
        assertFalse("wallpaper freshness must not use delayed heuristics", source.contains("postDelayed"));
        assertFalse("wallpaper freshness must not install a broadcast fallback",
                source.contains("BroadcastReceiver") || source.contains("IntentFilter")
                        || source.contains("ACTION_WALLPAPER_CHANGED"));
        assertFalse("wallpaper freshness must not poll", source.contains("Timer")
                || source.contains("ScheduledExecutor") || source.contains("while (true)"));
    }

    @Test public void callbacksAreInstalledIndependentlyAndPipelineOwnsInstallation() throws Exception {
        String source = hook();
        String pipeline = pipeline();

        assertTrue("optional vendor callbacks must be installed independently",
                source.contains("installWallpaperChanged")
                        && source.contains("installCandidate")
                        && source.contains("installFirstFrameRendered")
                        && source.contains("installDrawFrameEnd"));
        assertTrue("wallpaper hook belongs to the active zero-copy material pipeline",
                pipeline.contains("LauncherWallpaperFreshnessHook.install(classLoader)"));
    }
}
