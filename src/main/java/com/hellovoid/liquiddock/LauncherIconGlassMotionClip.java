package com.hellovoid.liquiddock;

import android.graphics.Path;
import android.graphics.RectF;
import android.view.SurfaceControl;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Launcher 4.50's real icon animation owners. No extra animator:
 * update() passes the OEM's interpolated progress and corner radius every frame.
 */
final class LauncherIconGlassMotionClip {
    private static final String TAG = "[DC][IconMotionClip]";
    private static Field viewClip;
    private static Field viewShadeClip;
    private static Field layerIcon;
    private static Field layerShader;
    private static Field layerValidRect;
    private static Field surfaceCompatControl;
    private static Method transactionGetter;
    private static boolean viewReady;
    private static boolean layerReady;

    private LauncherIconGlassMotionClip() {}

    static void prepare(ClassLoader loader) {
        try {
            Class<?> viewClass = Class.forName(
                    "com.miui.home.recents.views.FloatingIconView2", false, loader);
            viewClip = HookUtil.findField(viewClass, "mClipPath");
            viewShadeClip = HookUtil.findField(viewClass, "mShadeClipPath");
            viewReady = true;
        } catch (Throwable error) {
            MainHook.log(TAG + " View2 clip unavailable: " + error);
        }
        try {
            Class<?> layerClass = Class.forName(
                    "com.miui.home.recents.views.FloatingIconLayer2", false, loader);
            Class<?> wrapperClass = Class.forName(
                    "com.android.systemui.shared.recents.system.SurfaceControlCompat",
                    false, loader);
            Class<?> transactionClass = Class.forName(
                    "com.android.systemui.shared.recents.system.TransactionCompat",
                    false, loader);
            layerIcon = HookUtil.findField(layerClass, "mFloatingIconSurfaceControl");
            layerShader = HookUtil.findField(layerClass, "mFloatingIconShaderSurfaceControl");
            layerValidRect = HookUtil.findField(layerClass, "mValidIconRectF");
            surfaceCompatControl = HookUtil.findField(wrapperClass, "mSurfaceControl");
            transactionGetter = HookUtil.findMethodExact(
                    transactionClass, "getTransaction", new Class<?>[0]);
            layerReady = true;
        } catch (Throwable error) {
            MainHook.log(TAG + " Layer2 SurfaceControl unavailable: " + error);
        }
        MainHook.log(TAG + " prepared View2=" + viewReady + " Layer2=" + layerReady);
    }

    static void apply(Object owner, Object nativeResult, View source,
                      boolean layer2, RectF rotationRect,
                      float nativeProgress, float nativeRadius,
                      boolean closeToHome) {
        if (source == null || !LauncherIconGlassClip.hasGlass(source)) return;
        float targetRadius = LauncherIconGlassClip.targetRadiusPx(source);
        float radius = LauncherIconGlassMotionPolicy.radius(
                nativeRadius, targetRadius, nativeProgress, closeToHome);
        if (!Float.isFinite(radius)) return;
        if (layer2) applyLayer2(owner, nativeResult, rotationRect, radius);
        else if (owner instanceof View) applyView2((View) owner, radius);
    }

    private static void applyView2(View proxy, float radiusScreen) {
        if (!viewReady) return;
        try {
            Path nativePath = (Path) viewClip.get(proxy);
            if (nativePath == null || nativePath.isEmpty()) return;
            RectF bounds = new RectF();
            nativePath.computeBounds(bounds, true);
            if (bounds.isEmpty()) return;
            // Identical conversion to FloatingIconView2.updateClipPath().
            float scale = Math.max(1f, Math.min(proxy.getScaleX(), proxy.getScaleY()));
            float localRadius = LauncherIconGlassMotionPolicy.localRadius(radiusScreen, scale);
            float capped = LauncherGlassBoundsPolicy.capRadius(
                    localRadius, bounds.width(), bounds.height());
            LiquidDockConfig.Glass config = LiveGlassConfigState.currentGlass();
            if (config == null) return;
            LauncherIconGlassShapePath.build(nativePath, bounds.left, bounds.top,
                    bounds.right, bounds.bottom, capped, config.continuousCorners);
            // OEM drawBackground/drawForeground both clip directly against mClipPath.
            // The next update() resets the same Path, so no persistent OEM state is held.
            Path shade = (Path) viewShadeClip.get(proxy);
            if (shade != null && !shade.isEmpty()) {
                RectF shadeBounds = new RectF();
                shade.computeBounds(shadeBounds, true);
                float shadeRadius = LauncherGlassBoundsPolicy.capRadius(
                        localRadius, shadeBounds.width(), shadeBounds.height());
                LauncherIconGlassShapePath.build(shade, shadeBounds.left, shadeBounds.top,
                        shadeBounds.right, shadeBounds.bottom, shadeRadius,
                        config.continuousCorners);
            }
            logAppliedOnce("View2");
            proxy.invalidate();
        } catch (Throwable error) {
            logOnce("View2", error);
        }
    }

    private static void applyLayer2(Object owner, Object result,
                                    RectF rect, float radiusScreen) {
        if (!layerReady || result == null || rect == null) return;
        try {
            RectF valid = (RectF) layerValidRect.get(owner);
            if (valid == null) return;
            float scale = LauncherIconGlassMotionPolicy.layerScale(
                    rect.width(), rect.height(), valid.width(), valid.height());
            float localRadius = LauncherIconGlassMotionPolicy.localRadius(radiusScreen, scale);
            if (!Float.isFinite(localRadius)) return;
            Object value = transactionGetter.invoke(result);
            if (!(value instanceof SurfaceControl.Transaction)) return;
            SurfaceControl.Transaction txn = (SurfaceControl.Transaction) value;
            Object mainWrapper = layerIcon.get(owner);
            SurfaceControl main = asSurface(mainWrapper);
            if (main == null || !main.isValid()) return;
            // Override in the *same pending transaction* that the OEM returns.
            // No extra apply(), no inter-frame Surface round-trip and no new timer.
            txn.setCornerRadius(main, localRadius);
            SurfaceControl shader = asSurface(layerShader.get(owner));
            if (shader != null && shader.isValid()) {
                txn.setCornerRadius(shader, localRadius);
            }
            logAppliedOnce("Layer2");
        } catch (Throwable error) {
            logOnce("Layer2", error);
        }
    }

    private static SurfaceControl asSurface(Object wrapper) throws IllegalAccessException {
        if (wrapper == null) return null;
        Object value = surfaceCompatControl.get(wrapper);
        return value instanceof SurfaceControl ? (SurfaceControl) value : null;
    }

    private static final java.util.HashSet<String> LOGGED = new java.util.HashSet<>();
    private static void logAppliedOnce(String name) {
        synchronized (LOGGED) {
            if (LOGGED.add("applied-" + name)) {
                MainHook.log(TAG + " " + name + " native animated clip active");
            }
        }
    }
    private static void logOnce(String name, Throwable error) {
        synchronized (LOGGED) {
            if (LOGGED.add(name)) MainHook.log(TAG + " " + name + " failed: " + error);
        }
    }
}
