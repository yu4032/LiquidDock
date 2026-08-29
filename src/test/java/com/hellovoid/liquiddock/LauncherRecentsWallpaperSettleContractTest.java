package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract for HOME -> Recents -> HOME wallpaper scale settling. */
public class LauncherRecentsWallpaperSettleContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void recentsHideWaitsForWallpaperScaleToSettleBeforeFreshCapture()
            throws Exception {
        String hook = Files.readString(MAIN.resolve("LauncherGlassRecentsHook.java"));
        String controller = Files.readString(MAIN.resolve("LauncherGlassSceneController.java"));

        assertTrue(hook.contains("RECENTS_WALLPAPER_SETTLE_MS"));
        assertTrue(hook.contains("600L"));
        assertTrue(hook.contains("recentsReturnToken"));
        assertTrue(hook.contains("postDelayed"));
        assertTrue(hook.contains("setRecentsWallpaperSettlePendingForAll(true)"));
        assertTrue(hook.contains("setRecentsWallpaperSettlePendingForAll(false)"));

        int barrier = hook.indexOf("setRecentsWallpaperSettlePendingForAll(true)");
        int uncover = hook.indexOf("setRecentsCoveredForAll(false)", barrier);
        assertTrue("capture barrier must be armed before Recents is uncovered",
                barrier >= 0 && uncover > barrier);

        assertTrue(controller.contains("recentsWallpaperSettlePending"));
        assertTrue(controller.contains("setRecentsWallpaperSettlePendingForAll"));
        assertTrue(controller.contains("recentsWallpaperSettlePending"));
        assertTrue(controller.contains("isPresentationPending"));
    }
}
