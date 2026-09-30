package com.hellovoid.liquiddock;

import java.util.Locale;

/** Pure selection policy for desktop wallpaper surfaces that should stay in client composition. */
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
