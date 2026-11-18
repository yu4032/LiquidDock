package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.view.SurfaceControl;
import com.hellovoid.liquiddock.config.ConfigSchema;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps selected desktop wallpaper surfaces on SurfaceFlinger client/GPU composition without
 * globally disabling hardware overlays.
 */
final class WallpaperClientCompositionHook {
    private static final String TAG = "[DC][WallpaperClientComposition]";
    private static final float SHEAR_EPSILON = 0.00001f;

    private static final Map<SurfaceControl, Boolean> FORCE_CLIENT_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static boolean attempted;
    private static boolean installed;
    private static Method getNameMethod;
    private static volatile boolean liveEnabled;
    private static SharedPreferences preferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;

    private WallpaperClientCompositionHook() {}

    /** Delayed one-time system Hook installation when the user enables this option. */
    static synchronized void initialize(SharedPreferences next, boolean initialEnabled) {
        if (preferences != null && listener != null) {
            preferences.unregisterOnSharedPreferenceChangeListener(listener);
        }
        preferences = next;
        liveEnabled = initialEnabled;
        if (initialEnabled) install();
        listener = (prefs, key) -> {
            if (key != null
                    && !ConfigSchema.Core.ENABLED.name().equals(key)
                    && !ConfigSchema.Glass.ENABLED.name().equals(key)
                    && !ConfigSchema.Glass.WALLPAPER_FLICKER_FIX.name().equals(key)) return;
            boolean enabled = prefs.getBoolean(ConfigSchema.Core.ENABLED.name(),
                    ConfigSchema.Core.ENABLED.runtimeFallback())
                    && prefs.getBoolean(ConfigSchema.Glass.ENABLED.name(),
                    ConfigSchema.Glass.ENABLED.runtimeFallback())
                    && prefs.getBoolean(ConfigSchema.Glass.WALLPAPER_FLICKER_FIX.name(),
                    ConfigSchema.Glass.WALLPAPER_FLICKER_FIX.runtimeFallback());
            liveEnabled = enabled;
            if (enabled) install();
        };
        if (next != null) next.registerOnSharedPreferenceChangeListener(listener);
    }

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
                    if (liveEnabled && shouldForceClient(surface)) {
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
            } catch (Throwable ignored) {}
        }
        return String.valueOf(surface);
    }
}
