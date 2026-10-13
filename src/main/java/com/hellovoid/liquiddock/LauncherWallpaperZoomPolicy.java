package com.hellovoid.liquiddock;

/**
 * Restricts the vendor wallpaper animation's zoom target without touching its
 * animation lifecycle, scrolling offsets, surfaces or wallpaper content.
 *
 * <p>HyperOS Launcher WallpaperParam.getZoomOut() uses 1f as the neutral,
 * unscaled wallpaper target (the vendor zoom-out target can be less than 1f).
 * Do not rewrite invalid or unexpected return types: let the vendor handle them.
 */
final class LauncherWallpaperZoomPolicy {
    static final float NEUTRAL_ZOOM = 1.0f;

    private LauncherWallpaperZoomPolicy() {}

    static Object target(boolean disableZoom, Object vendorZoom) {
        if (!disableZoom || !(vendorZoom instanceof Float)) return vendorZoom;
        float zoom = ((Float) vendorZoom).floatValue();
        if (!Float.isFinite(zoom) || zoom <= 0f) return vendorZoom;
        return Float.valueOf(NEUTRAL_ZOOM);
    }
}
