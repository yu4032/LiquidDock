package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static vendor/API boundary contract for the HyperOS 4.50 wallpaper bridge. */
public class LauncherWallpaperFreshnessHookContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path HOOK = MAIN.resolve("LauncherWallpaperFreshnessHook.java");

    private static String hook() throws Exception {
        assertTrue("missing LauncherWallpaperFreshnessHook", Files.exists(HOOK));
        return Files.readString(HOOK);
    }

    @Test public void followsRawBinderCallbackAndVendorCacheTask() throws Exception {
        String source = hook();

        assertTrue(source.contains(
                "DesktopWallpaperManager$MiuiWallpaperManagerCallbackStub"));
        assertTrue(source.contains(
                "DesktopWallpaperManager$WallpaperInfoUpdateTask"));
        assertTrue(source.contains(
                "\"onWallpaperChanged\", WallpaperColors.class, String.class, int.class"));
        assertTrue(source.contains("task.getDeclaredMethod(\"run\")"));
        assertTrue(source.contains("LauncherGlassSceneController.onWallpaperChangedForAll()"));
        assertTrue(source.contains("LauncherGlassSceneController.onWallpaperCandidateForAll()"));
        assertTrue(source.contains("LauncherWallpaperTransactionState"));
    }

    @Test public void freshnessDoesNotDependOnInlineableManagerHelperHooks() throws Exception {
        String source = hook();

        assertFalse(source.contains("getDeclaredMethod(\"updateWallpaperInfo\")"));
        assertFalse(source.contains("getDeclaredMethod(\"notifyWallpaperColorChanged\")"));
        assertTrue(source.contains("TRANSACTION.onWallpaperChanged()"));
        assertTrue(source.contains("TRANSACTION.onTaskStarted()"));
        assertTrue(source.contains("TRANSACTION.shouldPublishTaskCompletion(serial)"));
    }

    @Test public void workspaceFreshnessDoesNotInferIdentityFromFrameworkWallpaperSignals()
            throws Exception {
        String source = hook();
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));

        assertFalse(source.contains("ACTION_WALLPAPER_CHANGED"));
        assertFalse(source.contains("WallpaperManager.getInstance"));
        assertFalse(source.contains("getWallpaperId"));
        assertFalse(source.contains("OnColorsChangedListener"));
        assertFalse(source.contains("WallpaperChangeIdentityState"));
        assertFalse(pipeline.contains("LauncherWallpaperFreshnessHook.attachContext"));
    }

    @Test public void firstFrameAndDrawEndAreNotWorkspaceWallpaperCompletionAuthorities()
            throws Exception {
        String source = hook();

        assertFalse(source.contains("onWallpaperFirstFrameRendered"));
        assertFalse(source.contains("LauncherGlassSceneController.onWallpaperAuthoritativeForAll()"));
        // The same callback still has a proven, separate Recents-return settle role.
        assertTrue(source.contains("LauncherGlassRecentsHook::onSystemWallpaperDrawFrameEnd"));
    }

    @Test public void bridgeDoesNotIntroducePollingOrDelayedFallbackApis() throws Exception {
        String source = hook();
        String recents = Files.readString(MAIN.resolve("LauncherGlassRecentsHook.java"));

        assertFalse(source.contains("postDelayed"));
        assertFalse(source.contains("Timer"));
        assertFalse(source.contains("ScheduledExecutor"));

        assertFalse(recents.contains("RECENTS_WALLPAPER_SETTLE_MS"));
        assertFalse(recents.contains("postDelayed("));
        assertFalse(recents.contains("PixelCopy"));
        assertFalse(recents.contains("ScreenCapture"));
        assertFalse(recents.contains("Bitmap"));
    }

    @Test public void recentsReturnStillUsesVendorAnimationCompletionBoundaries() throws Exception {
        String source = hook();
        String recents = Files.readString(MAIN.resolve("LauncherGlassRecentsHook.java"));

        assertTrue(recents.contains("com.miui.home.recents.anim.LocalWallpaperElement"));
        assertTrue(recents.contains("com.miui.home.recents.anim.SystemWallpaperElement"));
        assertTrue(recents.contains("com.miui.home.recents.anim.HyperSpringAnimation"));
        assertTrue(recents.contains("com.miui.home.recents.anim.MultiSpringDynamicAnimation"));
        assertTrue(recents.contains("\"doAnimationFrame\""));
        assertTrue(recents.contains("\"setFinalPosition\""));
        assertTrue(source.contains("LauncherGlassRecentsHook::onSystemWallpaperDrawFrameEnd"));
        assertTrue(recents.contains("onSystemWallpaperDrawFrameEnd"));
    }

    @Test public void recentsReturnWithoutVendorWallpaperAuthorityCannotWedgeFreshness()
            throws Exception {
        String recents = Files.readString(MAIN.resolve("LauncherGlassRecentsHook.java"));

        assertTrue(recents.contains("WALLPAPER_SETTLE.hasCompletionAuthority(serial)"));
        assertTrue(recents.contains(
                "cancelWallpaperSettle(serial, \"no-vendor-wallpaper-authority\")"));
        assertTrue(recents.contains("WALLPAPER_SETTLE.armCompletionAuthority(serial)"));
        assertFalse(recents.contains("RECENTS_WALLPAPER_SETTLE_MS"));
        assertFalse(recents.contains("postDelayed("));
    }

    @Test public void activeZeroCopyPipelineInstallsWallpaperBridge() throws Exception {
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));
        assertTrue(pipeline.contains("LauncherWallpaperFreshnessHook.install(classLoader)"));
    }
}
