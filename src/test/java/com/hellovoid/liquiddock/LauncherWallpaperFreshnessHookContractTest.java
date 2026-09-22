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

    @Test public void followsExactDesktopWallpaperManagerRefreshTransaction() throws Exception {
        String source = hook();

        assertTrue(source.contains(
                "com.miui.home.launcher.wallpaper.DesktopWallpaperManager"));
        assertTrue(source.contains("\"updateWallpaperInfo\""));
        assertTrue(source.contains("\"notifyWallpaperColorChanged\""));
        assertTrue(source.contains("LauncherGlassSceneController.onWallpaperChangedForAll()"));
        assertTrue(source.contains("LauncherGlassSceneController.onWallpaperCandidateForAll()"));
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

    @Test public void workstationWallpaperPulseReassertsSurfaceFlingerUpdateAuthority()
            throws Exception {
        String source = hook();
        String scene = Files.readString(MAIN.resolve("LauncherGlassSceneController.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));

        // Vendor completion remains notifyWallpaperColorChanged; do not invent a timer/rebind boundary.
        assertTrue(source.contains("\"notifyWallpaperColorChanged\""));
        assertTrue(scene.contains("LauncherWallpaperContentState.Pulse"));
        assertTrue(session.contains("sourceBackend.requestFresh("));
        assertTrue(bridge.contains("binding.domain == PassBlurDomain.LAUNCHER_WORKSPACE"));
        assertTrue(bridge.contains("WorkstationProducerPolicy.shouldForceWorkspaceResume("));
        assertFalse(source.contains("postDelayed"));
        assertFalse(source.contains("requestRebind"));
    }

    @Test public void activeZeroCopyPipelineInstallsWallpaperBridge() throws Exception {
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));
        assertTrue(pipeline.contains("LauncherWallpaperFreshnessHook.install(classLoader)"));
    }
}
