package com.hellovoid.liquiddock;

/**
 * Prevents the Launcher-issued wallpaper zoom target, leaving its vendor
 * spring, Recents settle callbacks and wallpaper position updates untouched.
 *
 * <p>Only hooked in com.miui.home. Both LocalWallpaperElement and
 * SystemWallpaperElement consume WallpaperParam; no global WallpaperManager
 * or SurfaceFlinger interception is necessary.
 */
final class LauncherWallpaperZoomHook {
    private static final String TAG = "[DC][WallpaperZoom]";
    private static final String WALLPAPER_PARAM =
            "com.miui.home.recents.anim.WallpaperParam";

    private static volatile boolean disableZoom;
    private static volatile boolean installed;

    private LauncherWallpaperZoomHook() {}

    static void onLiveConfigChanged(LiquidDockConfig config) {
        disableZoom = config != null && config.enabled && config.animation.disableWallpaperZoom;
    }

    static void install(ClassLoader classLoader) {
        if (installed || classLoader == null) return;
        try {
            HookUtil.hookMethod(classLoader, WALLPAPER_PARAM, "getZoomOut", chain -> {
                Object original = chain.proceed(chain.getArgs().toArray(new Object[0]));
                return LauncherWallpaperZoomPolicy.target(disableZoom, original);
            });
            installed = true;
            MainHook.log(TAG + " WallpaperParam.getZoomOut installed; opt-in=" + disableZoom);
        } catch (Throwable error) {
            // On incompatible Launcher versions retain vendor zoom behavior.
            MainHook.log(TAG + " unavailable; preserving stock wallpaper animation: " + error);
        }
    }
}
