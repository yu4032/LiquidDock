package com.hellovoid.liquiddock;

import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mirrors applied wallpaper transforms in the HWUI backdrop without changing the
 * vendor animation. Native WMS samples drive HyperOS animations; the Launcher-local
 * WallpaperManager observer supplies the current transform before native connection.
 */
final class LauncherWallpaperZoomHook {
    private static final String TAG = "[DC][WallpaperZoom]";
    private static final String WALLPAPER_PARAM =
            "com.miui.home.recents.anim.WallpaperParam";

    private static final AtomicBoolean FIRST_INTERCEPTION_REPORTED = new AtomicBoolean();
    private static final AtomicBoolean FIRST_ZOOM_UPDATE_REPORTED = new AtomicBoolean();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final java.util.concurrent.ConcurrentHashMap<Integer, LauncherWallpaperTransformState>
            NATIVE_TRANSFORMS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final AtomicBoolean INVALIDATION_QUEUED = new AtomicBoolean();
    private static final AtomicBoolean NATIVE_REPORTED = new AtomicBoolean();
    private static volatile LauncherWallpaperTransformState.Sample fallbackTransform =
            LauncherWallpaperTransformState.Sample.NEUTRAL;
    private static volatile boolean disableZoom;
    private static volatile boolean installed;
    private static volatile boolean wallpaperZoomHookInstalled;
    private static volatile boolean systemWallpaperElementHookInstalled;
    // HyperOS home starts at the neutral, unscaled wallpaper state.
    private static volatile float currentWallpaperSurfaceScale = 1f;
    private static volatile boolean wallpaperScaleRangeResolved;
    private static volatile float wallpaperMinScale = 1.0f;
    private static volatile float wallpaperMaxScale = 1.10f;

    private LauncherWallpaperZoomHook() {}

    static void onLiveConfigChanged(LiquidDockConfig config) {
        boolean next = config != null && config.enabled && config.animation.disableWallpaperZoom;
        if (next != disableZoom) FIRST_INTERCEPTION_REPORTED.set(false);
        disableZoom = next;
    }

    static void install(ClassLoader classLoader) {
        if (installed || classLoader == null) return;
        boolean paramHookInstalled = false;
        try {
            HookUtil.hookMethod(classLoader, WALLPAPER_PARAM, "getZoomOut", chain -> {
                Object original = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (disableZoom && original instanceof Float
                        && FIRST_INTERCEPTION_REPORTED.compareAndSet(false, true)) {
                    MainHook.log(TAG + " first vendor zoom target=" + original
                            + " -> neutral=" + LauncherWallpaperZoomPolicy.NEUTRAL_ZOOM);
                }
                return LauncherWallpaperZoomPolicy.target(disableZoom, original);
            });
            paramHookInstalled = true;
        } catch (Throwable error) {
            // On incompatible Launcher versions retain vendor zoom behavior.
            MainHook.log(TAG + " WallpaperParam.getZoomOut unavailable: " + error);
        }
        try {
            HookUtil.hookMethod(classLoader,
                    "android.app.WallpaperManager", "setWallpaperZoomOut", chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        if (args.length > 1 && args[1] instanceof Float) {
                            updateWallpaperManagerZoomOut(
                                    ((Float) args[1]).floatValue(), "manager");
                        }
                        return result;
                    }, "android.os.IBinder", float.class);
            wallpaperZoomHookInstalled = true;
            if (BuildConfig.DEBUG) {
                android.util.Log.i(TAG, "WallpaperManager zoom observer installed");
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " WallpaperManager zoom observer unavailable: " + error);
            if (BuildConfig.DEBUG) {
                android.util.Log.w(TAG, "WallpaperManager zoom observer unavailable", error);
            }
        }
        try {
            // Register for applied native transforms through the existing wallpaper command.
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.recents.anim.SystemWallpaperElement", "sendWallpaperCommand",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (args.length > 0 && args[0] instanceof Bundle) {
                            observeSystemWallpaperCommand((Bundle) args[0]);
                        }
                        return chain.proceed(args);
                    }, "android.os.Bundle");
            systemWallpaperElementHookInstalled = true;
            if (BuildConfig.DEBUG) {
                android.util.Log.i(TAG, "SystemWallpaperElement command observer installed");
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " SystemWallpaperElement observer unavailable: " + error);
            if (BuildConfig.DEBUG) {
                android.util.Log.w(TAG, "SystemWallpaperElement observer unavailable", error);
            }
        }
        installed = paramHookInstalled || wallpaperZoomHookInstalled
                || systemWallpaperElementHookInstalled;
        MainHook.log(TAG + " hooks installed param=" + paramHookInstalled
                + " zoomObserver=" + wallpaperZoomHookInstalled
                + " systemElementObserver=" + systemWallpaperElementHookInstalled
                + "; opt-in=" + disableZoom);
    }

    private static void observeSystemWallpaperCommand(Bundle command) {
        // Targets cannot describe the current native spring. WMS returns applied transforms
        // through this callback; do not jump to setTo/startAnim endpoints in the Launcher.
        WallpaperNativeTransformBridge.attach(command);
    }

    static void onNativeTransform(int displayId,long timestampNs,float scale,float pivotX,float pivotY) {
        LauncherWallpaperTransformState state = NATIVE_TRANSFORMS.computeIfAbsent(
                displayId, ignored -> new LauncherWallpaperTransformState());
        if (!state.update(timestampNs,scale,pivotX,pivotY)) return;
        if (NATIVE_REPORTED.compareAndSet(false,true)) {
            MainHook.log(TAG + " native applied transforms connected display=" + displayId);
            MAIN_HANDLER.post(LauncherGlassSessionRegistry::onNativeWallpaperTransformConnected);
        }
        if (INVALIDATION_QUEUED.compareAndSet(false,true)) {
            MAIN_HANDLER.post(() -> {
                INVALIDATION_QUEUED.set(false);
                LauncherGlassSessionRegistry.onWallpaperZoomOutChanged();
            });
        }
    }

    static LauncherWallpaperTransformState.Sample currentTransform(int displayId) {
        LauncherWallpaperTransformState state = NATIVE_TRANSFORMS.get(displayId);
        LauncherWallpaperTransformState.Sample sample = state != null ? state.latest() : null;
        return sample != null ? sample : fallbackTransform;
    }

    static LauncherWallpaperTransformState.Sample capturedTransform(int displayId,long timestampNs) {
        // Connection and the first oneway UPDATE may lag behind capture. Neither an empty
        // client history nor a local target authorizes tagging an immutable background.
        return WallpaperNativeTransformBridge.capture(displayId,timestampNs);
    }
    private static void updateWallpaperManagerZoomOut(float zoomOut, String source) {
        if (!Float.isFinite(zoomOut) || zoomOut < 0f || zoomOut > 1f) return;
        resolveWallpaperScaleRange();
        float scale = wallpaperMinScale + (wallpaperMaxScale - wallpaperMinScale)
                * (1f - zoomOut);
        updateWallpaperSurfaceScale(scale, source);
    }

    private static void updateWallpaperSurfaceScale(float surfaceScale, String source) {
        if (!Float.isFinite(surfaceScale) || surfaceScale <= 0f || surfaceScale > 4f) return;
        if (Float.floatToIntBits(currentWallpaperSurfaceScale)
                == Float.floatToIntBits(surfaceScale)) return;
        currentWallpaperSurfaceScale = surfaceScale;
        fallbackTransform = new LauncherWallpaperTransformState.Sample(
                System.nanoTime(), surfaceScale, .5f, .5f);
        if (FIRST_ZOOM_UPDATE_REPORTED.compareAndSet(false, true)) {
            String message = "first wallpaper zoom update source=" + source
                    + " surfaceScale=" + surfaceScale;
            MainHook.log(TAG + " " + message);
            if (BuildConfig.DEBUG) android.util.Log.i(TAG, message);
        }
        LauncherGlassSessionRegistry.onWallpaperZoomOutChanged();
    }

    private static void resolveWallpaperScaleRange() {
        if (wallpaperScaleRangeResolved) return;
        synchronized (LauncherWallpaperZoomHook.class) {
            if (wallpaperScaleRangeResolved) return;
            float min = 1.0f;
            float max = 1.10f;
            try {
                Resources resources = Resources.getSystem();
                int minId = resources.getIdentifier(
                        "config_wallpaperMinScale", "dimen", "android");
                int maxId = resources.getIdentifier(
                        "config_wallpaperMaxScale", "dimen", "android");
                if (minId != 0) min = resources.getFloat(minId);
                if (maxId != 0) max = resources.getFloat(maxId);
                if (!Float.isFinite(min) || !Float.isFinite(max) || min <= 0f || max < min) {
                    min = 1.0f;
                    max = 1.10f;
                }
            } catch (Throwable error) {
                MainHook.log(TAG + " scale resources unavailable; using framework defaults: " + error);
            }
            wallpaperMinScale = min;
            wallpaperMaxScale = max;
            wallpaperScaleRangeResolved = true;
            MainHook.log(TAG + " wallpaper surface scale range=" + min + ".." + max);
        }
    }
}
