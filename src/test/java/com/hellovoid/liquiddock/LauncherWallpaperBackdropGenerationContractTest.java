package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Focused integration guards for WallpaperContentGeneration routing. */
public class LauncherWallpaperBackdropGenerationContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(String name) throws Exception {
        return Files.readString(MAIN.resolve(name));
    }

    @Test public void wallpaperContentGenerationStaysIndependentFromSceneGeneration()
            throws Exception {
        String controller = read("LauncherGlassSceneController.java");
        assertTrue(controller.contains("LauncherWallpaperContentState wallpaperContentState"));
        assertTrue(controller.contains("onWallpaperChangedForAll()"));
        assertFalse(controller.contains("onWallpaperChanged() {\n        state.onGenerationInvalidated()"));
    }

    @Test public void sessionOwnsWallpaperFrameTokenInsteadOfGuessingFromGenericFreshFrame()
            throws Exception {
        String controller = read("LauncherGlassSceneController.java");
        String session = read("LauncherGlassSession.java");
        assertTrue("Session must accept the content token with the producer pulse",
                session.contains("requestWallpaperBackdrop("));
        assertTrue("Session must snapshot the requested wallpaper generation",
                session.contains("wallpaperRequestedGeneration"));
        assertTrue("Controller must send the content generation into Session",
                controller.contains("session.requestWallpaperBackdrop("));
        assertFalse("generic fresh-frame acknowledgement must not consume a wallpaper token",
                controller.contains("controller.onWallpaperFrameConsumed(generation)"));
    }
}
