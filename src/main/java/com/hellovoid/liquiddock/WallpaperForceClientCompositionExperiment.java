package com.hellovoid.liquiddock;

import android.view.SurfaceControl;

import java.lang.reflect.Method;

/**
 * Temporary device experiment: keep the desktop wallpaper on SurfaceFlinger client/GPU
 * composition without globally disabling HWC overlays.
 *
 * <p>SurfaceFlinger marks a layer transform with ROT_INVALID as requiring client composition.
 * A tiny non-zero shear is visually negligible but makes the wallpaper transform no longer
 * representable by the HWC rotation/scale-only transform. This is intentionally restricted to
 * system_server's desktop wallpaper animation transactions.</p>
 */
final class WallpaperForceClientCompositionExperiment {
    private static final String TAG = "[DC][WALLPAPER_CLIENT]";
    private static final float SHEAR_EPSILON = 0.00001f;

    private static boolean attempted;
    private static boolean installed;
    private static String lastLoggedSurface;

    private WallpaperForceClientCompositionExperiment() {}

    static synchronized boolean install() {
        if (attempted) return installed;
        attempted = true;

        try {
            Method method = SurfaceControl.Transaction.class.getDeclaredMethod(
                    "setMatrix",
                    SurfaceControl.class,
                    float.class,
                    float.class,
                    float.class,
                    float.class);
            HookUtil.hook(method, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (args.length == 5
                        && args[0] instanceof SurfaceControl
                        && args[1] instanceof Number
                        && args[2] instanceof Number
                        && args[3] instanceof Number
                        && args[4] instanceof Number) {
                    SurfaceControl sc = (SurfaceControl) args[0];
                    String name = surfaceName(sc);
                    if (isDesktopWallpaper(name) && isWallpaperAnimationCaller()) {
                        float dtdx = ((Number) args[2]).floatValue();
                        float forcedDtdx = dtdx + SHEAR_EPSILON;
                        args[2] = Float.valueOf(forcedDtdx);

                        if (!name.equals(lastLoggedSurface)) {
                            lastLoggedSurface = name;
                            Api101Bridge.log(TAG
                                    + " force-client matrix surface=" + name
                                    + " dsdx=" + args[1]
                                    + " dtdx=" + dtdx + "->" + forcedDtdx
                                    + " dtdy=" + args[3]
                                    + " dsdy=" + args[4]);
                        }
                    }
                }
                return chain.proceed(args);
            });
            installed = true;
            Api101Bridge.log(TAG + " installed=true epsilon=" + SHEAR_EPSILON);
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " install failed", error);
            installed = false;
        }
        return installed;
    }

    private static boolean isDesktopWallpaper(String name) {
        if (name == null || name.isEmpty()) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (!lower.contains("wallpaper")) return false;
        if (lower.contains("keyguard")
                || lower.contains("pictorial")
                || lower.contains("lockscreen")
                || lower.contains("showwhenlocked=true")) {
            return false;
        }
        return lower.contains("imagewallpaper")
                || lower.contains("wallpaperwindowtoken")
                || lower.contains("miwallpaper.desktop");
    }

    private static boolean isWallpaperAnimationCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            if (frame == null) continue;
            String cls = frame.getClassName();
            if (cls == null) continue;
            if (cls.contains("MiuiWallpaperSurfaceAnimation")
                    || cls.contains("MiuiWallpaperAnimationManager")
                    || cls.contains("WallpaperSurfaceAnimation")) {
                return true;
            }
        }
        return false;
    }

    private static String surfaceName(SurfaceControl sc) {
        if (sc == null) return "";
        try {
            Method getName = SurfaceControl.class.getDeclaredMethod("getName");
            getName.setAccessible(true);
            Object value = getName.invoke(sc);
            if (value != null) return String.valueOf(value);
        } catch (Throwable ignored) {}
        return String.valueOf(sc);
    }
}
