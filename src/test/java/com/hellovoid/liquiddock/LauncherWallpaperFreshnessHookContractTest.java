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

    @Test public void bridgeDoesNotIntroducePollingOrDelayedFallbackApis() throws Exception {
        String source = hook();
        assertFalse(source.contains("postDelayed"));
        assertFalse(source.contains("BroadcastReceiver"));
        assertFalse(source.contains("IntentFilter"));
        assertFalse(source.contains("ACTION_WALLPAPER_CHANGED"));
        assertFalse(source.contains("Timer"));
        assertFalse(source.contains("ScheduledExecutor"));
    }

    @Test public void activeZeroCopyPipelineInstallsWallpaperBridge() throws Exception {
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));
        assertTrue(pipeline.contains("LauncherWallpaperFreshnessHook.install(classLoader)"));
    }
}
