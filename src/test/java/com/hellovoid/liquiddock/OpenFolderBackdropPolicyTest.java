package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class OpenFolderBackdropPolicyTest {
    @Test public void progressUsesNativeTranslationAndFallsBackToYForVerticalOnlyExpansion() throws Exception {
        java.lang.reflect.Method progress = OpenFolderBackdropPolicy.class.getDeclaredMethod("nativeProgress",
                float.class, float.class, float.class, float.class);
        assertEquals(.5f, (Float) progress.invoke(null, 100f, 50f, 200f, 100f), 0f);
        assertEquals(.75f, (Float) progress.invoke(null, 0f, 150f, 0f, 200f), 0f);
        assertEquals(1f, (Float) progress.invoke(null, -200f, -100f, -200f, -100f), 0f);
        assertTrue(Float.isNaN((Float) progress.invoke(null, 0f, 0f, 0f, 0f)));
    }
    @Test public void visualNativeScaleHandlesCenterAlignedFolderWithoutTranslation() {
        // Native Folme leaves measured width constant; only ScaleX moves.
        assertEquals(.5f, OpenFolderBackdropPolicy.nativeVisualProgress(
                0f, 0f, 0f, 0f, 2f, 3f), .0001f);
        // Both directions use identical coordinates, so close reverses smoothly.
        assertEquals(.75f, OpenFolderBackdropPolicy.nativeVisualProgress(
                0f, 0f, 0f, 0f, 2.5f, 3f), .0001f);
        assertEquals(.25f, OpenFolderBackdropPolicy.nativeVisualProgress(
                0f, 0f, 0f, 0f, 1.5f, 3f), .0001f);
        assertEquals(.5f, OpenFolderBackdropPolicy.nativeVisualProgress(
                50f, 0f, 100f, 0f, 50f, 3f), .0001f);
        assertTrue(Float.isNaN(OpenFolderBackdropPolicy.nativeVisualProgress(
                0f, 0f, 0f, 0f, 1f, 1f)));
    }
    @Test public void backgroundTracksNativeProgressAndClampsInvalidSamples() throws Exception {
        assertEquals(0f, opacity(0f), 0f);
        assertEquals(0.5f, opacity(0.5f), 0f);
        assertEquals(1f, opacity(1f), 0f);
        assertEquals(0f, opacity(Float.NaN), 0f);
        assertEquals(0f, opacity(Float.POSITIVE_INFINITY), 0f);
        assertEquals(0f, opacity(-1f), 0f);
        assertEquals(1f, opacity(2f), 0f);
    }

    @Test public void preparedDesktopBlurMapsFboRowsOntoLogicalFullscreenQuad() {
        assertEquals(1f, OpenFolderBackdropPolicy.backdropSampleY(0f), 0f);
        assertEquals(.75f, OpenFolderBackdropPolicy.backdropSampleY(.25f), 0f);
        assertEquals(0f, OpenFolderBackdropPolicy.backdropSampleY(1f), 0f);
    }

    @Test public void folderDesktopIncludesLauncherAndExcludesDock() {
        assertArrayEquals(new String[]{"FolderSource#1", "NavigationBar", "StatusBar",
                "GestureStub", "DockAssistantView"},
                PassBlurBindPolicy.exclusions("FolderSource#1",
                        PassBlurBindPolicy.folderDesktopExclusions("Launcher#1")));
    }
    @Test public void folderCaptureHasItsOwnRootWithoutWorkspaceUnlockGate() {
        PassBlurDomain folder;
        try { folder = PassBlurDomain.valueOf("OPEN_FOLDER"); }
        catch (IllegalArgumentException missing) { throw new AssertionError("Folder capture needs independent ownership", missing); }
        assertEquals(1f, PassBlurBindPolicy.nativeScale(folder, 0.25f), 0f);
        assertFalse(PassBlurBindPolicy.requiresUnlockGate(folder));
        assertEquals(24, PassBlurBindPolicy.retryBudgetAttempt(folder, 24, true, true));
        assertArrayEquals(new String[]{"FolderSource#1", "NavigationBar", "StatusBar",
                "GestureStub", "DockAssistantView"},
                PassBlurBindPolicy.exclusions("FolderSource#1",
                        PassBlurBindPolicy.folderDesktopExclusions("Launcher#1")));
        assertEquals(1f, PassBlurBindPolicy.nativeScale(PassBlurDomain.SHORTCUT_POPUP, 0.25f), 0f);
    }

    @Test public void backdropProgressFollowsExpansionAndContractionEvenWhenNativePlateAlphaIsZero() throws Exception {
        assertEquals(0f, progress(80, 600, 80, 80, 700, 80), 0f);
        assertEquals(0.5f, progress(80, 600, 340, 80, 700, 390), 0.001f);
        assertEquals(1f, progress(80, 600, 600, 80, 700, 700), 0f);
        assertEquals(0.5f, progress(80, 600, 340, 80, 700, 390), 0.001f);
        assertEquals(0.5f, progress(80, 80, 80, 80, 700, 390), 0.001f);
        assertEquals(0f, progress(80, 600, Float.NaN, 80, 700, 390), 0f);
    }

    private static float progress(float sw, float tw, float w, float sh, float th, float h) throws Exception {
        try {
            return (Float) Class.forName("com.hellovoid.liquiddock.OpenFolderBackdropPolicy")
                    .getDeclaredMethod("openProgress", float.class, float.class, float.class,
                            float.class, float.class, float.class).invoke(null, sw, tw, w, sh, th, h);
        } catch (NoSuchMethodException missing) { throw new AssertionError("Background must follow native geometric progress", missing); }
    }

    private static float opacity(float value) throws Exception {
        try {
            return (Float) Class.forName("com.hellovoid.liquiddock.OpenFolderBackdropPolicy")
                    .getDeclaredMethod("backdropOpacity", float.class).invoke(null, value);
        } catch (ClassNotFoundException missing) { throw new AssertionError("Missing folder backdrop composition policy", missing); }
    }
}
