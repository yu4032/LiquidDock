package com.hellovoid.liquiddock;

import android.view.SurfaceControl;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps the desktop wallpaper chain on SurfaceFlinger client/GPU composition without globally
 * disabling hardware overlays.
 *
 * <p>A tiny non-zero shear makes the wallpaper transform unsuitable for the hardware-composer
 * transform path while remaining visually negligible. Only verified desktop wallpaper surfaces
 * are modified; lockscreen and pictorial wallpaper surfaces remain vendor-owned.</p>
 */
final class WallpaperClientCompositionHook {
    private static final String TAG = "[DC][WallpaperClientComposition]";
    private static final float SHEAR_EPSILON = 0.00001f;

    private static final Map<SurfaceControl, Boolean> FORCE_CLIENT_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static boolean attempted;
    private static boolean installed;
    private static Method getNameMethod;

    private WallpaperClientCompositionHook() {}

    static synchronized boolean install() {
        if (attempted) return installed;
        attempted = true;

        try {
            try {
                getNameMethod = SurfaceControl.class.getDeclaredMethod("getName");
                getNameMethod.setAccessible(true);
            } catch (Throwable ignored) {
                getNameMethod = null;
            }

            Method setMatrix = SurfaceControl.Transaction.class.getDeclaredMethod(
                    "setMatrix",
                    SurfaceControl.class,
                    float.class,
                    float.class,
                    float.class,
                    float.class);

            HookUtil.hook(setMatrix, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (args.length == 5
                        && args[0] instanceof SurfaceControl
                        && args[2] instanceof Number) {
                    SurfaceControl surface = (SurfaceControl) args[0];
                    if (shouldForceClient(surface)) {
                        float dtdx = ((Number) args[2]).floatValue();
                        args[2] = Float.valueOf(dtdx + SHEAR_EPSILON);
                    }
                }
                return chain.proceed(args);
            });

            installed = true;
            Api101Bridge.log(TAG + " installed");
        } catch (Throwable error) {
            installed = false;
            Api101Bridge.log(TAG + " install failed", error);
        }
        return installed;
    }

    private static boolean shouldForceClient(SurfaceControl surface) {
        Boolean cached = FORCE_CLIENT_CACHE.get(surface);
        if (cached != null) return cached.booleanValue();

        boolean force = WallpaperClientCompositionPolicy.shouldForceClient(surfaceName(surface));
        FORCE_CLIENT_CACHE.put(surface, Boolean.valueOf(force));
        return force;
    }

    private static String surfaceName(SurfaceControl surface) {
        if (surface == null) return "";
        Method method = getNameMethod;
        if (method != null) {
            try {
                Object value = method.invoke(surface);
                if (value != null) return String.valueOf(value);
            } catch (Throwable ignored) {
                // Fall through to SurfaceControl.toString(), which also carries the debug name.
            }
        }
        return String.valueOf(surface);
    }
}
