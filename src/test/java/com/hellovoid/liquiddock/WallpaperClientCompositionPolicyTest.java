package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.liquiddock.config.PresetManager;

import org.junit.Test;

public class WallpaperClientCompositionPolicyTest {
    @Test
    public void flickerFixIsExplicitOptInRegisteredConfig() {
        assertFalse(ConfigSchema.Glass.WALLPAPER_FLICKER_FIX.uiDefault());
        assertFalse(ConfigSchema.Glass.WALLPAPER_FLICKER_FIX.runtimeFallback());
        assertTrue(ConfigSchema.all().contains(ConfigSchema.Glass.WALLPAPER_FLICKER_FIX));
        assertFalse((Boolean) PresetManager.defaultValues().get(
                ConfigSchema.Glass.WALLPAPER_FLICKER_FIX.name()));
    }

    @Test
    public void acceptsVerifiedDesktopWallpaperSurfaces() {
        assertTrue(WallpaperClientCompositionPolicy.shouldForceClient(
                "e0488a com.miui.miwallpaper.wallpaperservice.ImageWallpaper#60"));
        assertTrue(WallpaperClientCompositionPolicy.shouldForceClient(
                "WallpaperWindowToken{9911064 showWhenLocked=false}#59"));
        assertTrue(WallpaperClientCompositionPolicy.shouldForceClient(
                "Wallpaper BBQ wrapper#62"));
        assertTrue(WallpaperClientCompositionPolicy.shouldForceClient(
                "miui.miwallpaper.desktop.Wallpaper"));
    }

    @Test
    public void rejectsLockscreenAndUnrelatedSurfaces() {
        assertFalse(WallpaperClientCompositionPolicy.shouldForceClient(
                "Wallpaper BBQ wrapper-lock#65"));
        assertFalse(WallpaperClientCompositionPolicy.shouldForceClient(
                "WallpaperWindowToken{2287660 showWhenLocked=true}#59"));
        assertFalse(WallpaperClientCompositionPolicy.shouldForceClient(
                "com.miui.miwallpaper.wallpaperservice.MiuiKeyguardPictorialWallpaper"));
        assertFalse(WallpaperClientCompositionPolicy.shouldForceClient(
                "com.miui.home/com.miui.home.launcher.Launcher#3983"));
        assertFalse(WallpaperClientCompositionPolicy.shouldForceClient(null));
    }
}
