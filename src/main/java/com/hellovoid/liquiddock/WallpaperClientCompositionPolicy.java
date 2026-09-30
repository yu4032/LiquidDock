package com.hellovoid.liquiddock;

import java.util.Locale;

/** Selects desktop wallpaper surfaces that must remain on client/GPU composition. */
final class WallpaperClientCompositionPolicy {
    private WallpaperClientCompositionPolicy() {}

    static boolean shouldForceClient(String surfaceName) {
        if (surfaceName == null || surfaceName.isEmpty()) return false;
        String lower = surfaceName.toLowerCase(Locale.ROOT);
        if (!lower.contains("wallpaper")) return false;
        if (lower.contains("keyguard")
                || lower.contains("pictorial")
                || lower.contains("lockscreen")
                || lower.contains("showwhenlocked=true")
                || lower.contains("wrapper-lock")) {
            return false;
        }
        return lower.contains("imagewallpaper")
                || lower.contains("wallpaperwindowtoken")
                || lower.contains("miwallpaper.desktop")
                || lower.contains("wallpaper bbq wrapper");
    }
}
