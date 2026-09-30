package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.os.SystemClock;
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
    private static long lastCandidateLogUptime;

    private WallpaperForceClientCompositionExperiment() {}

    static synchronized boolean install() {
        if (attempted) return installed;
        attempted = true;

        boolean hooked = false;
        try {
            Method floatMatrix = SurfaceControl.Transaction.class.getDeclaredMethod(
                    "setMatrix",
                    SurfaceControl.class,
                    float.class,
                    float.class,
                    float.class,
                    float.class);
            HookUtil.hook(floatMatrix, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (args.length == 5
                        && args[0] instanceof SurfaceControl
                        && args[1] instanceof Number
                        && args[2] instanceof Number
                        && args[3] instanceof Number
                        && args[4] instanceof Number) {
                    SurfaceControl sc = (SurfaceControl) args[0];
                    String name = surfaceName(sc);
                    if (isWallpaperSurface(name)) {
                        logCandidate("float", name);
                    }
                    if (isDesktopWallpaper(name)) {
                        float dtdx = ((Number) args[2]).floatValue();
                        float forcedDtdx = dtdx + SHEAR_EPSILON;
                        args[2] = Float.valueOf(forcedDtdx);
                    }
                }
                return chain.proceed(args);
            });
            hooked = true;
            Api101Bridge.log(TAG + " hooked=float-matrix");
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " float-matrix hook unavailable", error);
        }

        try {
            Method objectMatrix = SurfaceControl.Transaction.class.getDeclaredMethod(
                    "setMatrix",
                    SurfaceControl.class,
                    Matrix.class,
                    float[].class);
            HookUtil.hook(objectMatrix, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (args.length == 3
                        && args[0] instanceof SurfaceControl
                        && args[1] instanceof Matrix) {
                    SurfaceControl sc = (SurfaceControl) args[0];
                    String name = surfaceName(sc);
                    if (isWallpaperSurface(name)) {
                        logCandidate("object", name);
                    }
                    if (isDesktopWallpaper(name)) {
                        Matrix forced = new Matrix((Matrix) args[1]);
                        forced.postSkew(SHEAR_EPSILON, 0.0f);
                        args[1] = forced;
                    }
                }
                return chain.proceed(args);
            });
            hooked = true;
            Api101Bridge.log(TAG + " hooked=object-matrix");
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " object-matrix hook unavailable", error);
        }

        installed = hooked;
        Api101Bridge.log(TAG + " installed=" + installed + " epsilon=" + SHEAR_EPSILON);
        return installed;
    }

    private static void logCandidate(String overload, String name) {
        long now = SystemClock.uptimeMillis();
        if (now - lastCandidateLogUptime < 250L) return;
        lastCandidateLogUptime = now;
        Api101Bridge.log(TAG + " candidate overload=" + overload + " surface=" + name
                + " desktopMatch=" + isDesktopWallpaper(name));
    }

    private static boolean isDesktopWallpaper(String name) {
        if (name == null || name.isEmpty()) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
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

    private static boolean isWallpaperSurface(String name) {
        if (name == null || name.isEmpty()) return false;
        return name.toLowerCase(java.util.Locale.ROOT).contains("wallpaper");
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
