package com.hellovoid.liquiddock;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Materialize icon-only alpha exactly once in an animation's cached resource.
 *
 * The OEM continues to own every subsequent frame's matrix, task-to-icon morph
 * and Surface transaction. Never alter FloatingIcon.update(), schedule another
 * invalidate(), or apply a transaction from here.
 */
final class LauncherIconGlassCachedAsset {
    private static final String TAG = "[DC][IconCachedAsset]";
    private static final Map<Object, MaskSpec> LAYER_REQUESTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Path> DRAW_MASK = new ThreadLocal<>();
    private static boolean installed;

    private static final class MaskSpec {
        final float radiusFraction;
        final boolean continuous;

        MaskSpec(float radiusFraction, boolean continuous) {
            this.radiusFraction = radiusFraction;
            this.continuous = continuous;
        }

        Path path(float width, float height) {
            Path shape = new Path();
            float radius = LauncherGlassBoundsPolicy.capRadius(
                    radiusFraction * Math.min(width, height), width, height);
            LauncherIconGlassShapePath.build(shape, 0f, 0f, width, height,
                    radius, continuous);
            return shape;
        }
    }

    /** Cache only immutable geometry; do not retain Launcher View or Drawable. */
    private static MaskSpec fromSource(View source) {
        if (source == null || !LauncherIconGlassClip.hasGlass(source)) return null;
        float radius = LauncherIconGlassClip.targetRadiusPx(source);
        LauncherGlassIconGeometry.Bounds bounds = LauncherGlassIconGeometry.resolve(source);
        LiquidDockConfig.Glass config = LiveGlassConfigState.currentGlass();
        if (config == null || bounds == null || !(bounds.width() > 0f)
                || !(bounds.height() > 0f) || !Float.isFinite(radius)) return null;
        return new MaskSpec(Math.max(0f, radius / Math.min(
                bounds.width(), bounds.height())), config.continuousCorners);
    }

    static synchronized void install(ClassLoader loader) {
        if (installed) return;
        installed = true;
        boolean view = installView2(loader);
        boolean layer = installLayer2(loader);
        MainHook.log(TAG + " resource-time hooks view2=" + view + " layer2=" + layer);
    }

    /** View2 caches bitmap icons at setIcon(); animated/adaptive icons stay native. */
    private static boolean installView2(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(
                    "com.miui.home.recents.views.FloatingIconView2", false, loader);
            Field background = HookUtil.findField(clazz, "mBackground");
            HookUtil.hook(HookUtil.findMethodExact(clazz, "setIcon", new Class<?>[0]),
                    chain -> {
                        Object owner = chain.getThisObject();
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (!(owner instanceof View) || !GlassRuntimeState.isAnyIconEnabled())
                            return result;
                        try {
                            cacheViewBitmap((View) owner, background);
                        } catch (Throwable error) {
                            logFailureOnce("View2", error);
                        }
                        return result;
                    });
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " View2 setup unavailable: " + error);
            return false;
        }
    }

    private static void cacheViewBitmap(View proxy, Field background) throws Exception {
                        Object owner = proxy;
                        HookUtil.InvocationResult<Object> target =
                                HookUtil.tryInvoke(owner, "getAnimTarget");
                        View source = target.succeeded() && target.value() instanceof View
                                ? (View) target.value() : null;
                        MaskSpec mask = fromSource(source);
                        if (mask == null) return;
                        Object image = background.get(owner);
                        // Preserve live/app-specific and layered adaptive icon animation.
                        if (!(image instanceof BitmapDrawable) || image instanceof Animatable)
                            return;
                        BitmapDrawable original = (BitmapDrawable) image;
                        int w = proxy.getWidth();
                        int h = proxy.getHeight();
                        if (w <= 0 || h <= 0 || w > 512 || h > 512) return;
                        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        canvas.clipPath(mask.path(w, h));
                        Rect saved = new Rect(original.getBounds());
                        try {
                            original.setBounds(0, 0, w, h);
                            original.draw(canvas);
                        } finally {
                            original.setBounds(saved);
                        }
                        BitmapDrawable clipped = new BitmapDrawable(proxy.getResources(), bitmap);
                        clipped.setBounds(saved);
                        background.set(owner, clipped);
                        logPreparedOnce("View2");
    }

    /**
     * Layer2 draws into a persistent icon Surface once on FloatingIconThread.
     * Apply the alpha mask to the existing buffer at that draw, not during update.
     */
    private static boolean installLayer2(ClassLoader loader) {
        try {
            Class<?> layer = Class.forName(
                    "com.miui.home.recents.views.FloatingIconLayer2", false, loader);
            Class<?> target = Class.forName(
                    "com.miui.home.launcher.anim.LaunchAppAndBackHomeAnimTarget", false, loader);
            Class<?> launcher = Class.forName("com.miui.home.launcher.Launcher", false, loader);
            Class<?> surfaceControl = Class.forName(
                    "com.android.systemui.shared.recents.system.SurfaceControlCompat",
                    false, loader);
            Class<?> surfaceCompat = Class.forName(
                    "com.android.systemui.shared.recents.system.SurfaceCompat", false, loader);
            Class<?> utils = Class.forName(
                    "com.android.systemui.shared.recents.system.SurfaceControlUtils", false, loader);
            Field validRect = HookUtil.findField(layer, "mValidIconRectF");

            HookUtil.hook(HookUtil.findMethodExact(layer, "init",
                    new Class<?>[]{launcher, target, surfaceControl,
                            boolean.class, boolean.class}), chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object owner = chain.getThisObject();
                MaskSpec spec = args.length > 1 && args[1] instanceof View
                        ? fromSource((View) args[1]) : null;
                if (spec != null) LAYER_REQUESTS.put(owner, spec);
                else LAYER_REQUESTS.remove(owner);
                return chain.proceed(args);
            });

            HookUtil.hook(HookUtil.findMethodExact(layer, "draw",
                    new Class<?>[]{int.class}), chain -> {
                Object owner = chain.getThisObject();
                MaskSpec spec = LAYER_REQUESTS.remove(owner);
                if (spec != null && GlassRuntimeState.isAnyIconEnabled()) {
                    try {
                        RectF bounds = (RectF) validRect.get(owner);
                        if (bounds != null && bounds.width() > 0f && bounds.height() > 0f
                                && bounds.width() <= 1024f && bounds.height() <= 1024f) {
                            DRAW_MASK.set(spec.path(bounds.width(), bounds.height()));
                        }
                    } catch (Throwable error) {
                        logFailureOnce("Layer2", error);
                    }
                }
                try {
                    // The original draw() writes one cached icon buffer.
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                } finally {
                    DRAW_MASK.remove();
                }
            });

            HookUtil.hook(HookUtil.findMethodExact(utils, "draw",
                    new Class<?>[]{surfaceCompat, int.class, Rect.class, Drawable[].class}),
                    chain -> {
                Path mask = DRAW_MASK.get();
                if (mask == null) return chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (args.length != 4 || !(args[3] instanceof Drawable[]))
                    return chain.proceed(args);
                Drawable[] originals = (Drawable[]) args[3];
                Drawable[] clipped = new Drawable[originals.length];
                for (int i = 0; i < originals.length; i++) {
                    clipped[i] = originals[i] == null ? null :
                            new SurfaceMaskDrawable(originals[i], mask);
                }
                args[3] = clipped;
                // Only runs while the actual native Surface buffer is being populated.
                logPreparedOnce("Layer2");
                return chain.proceed(args);
            });
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " Layer2 setup unavailable: " + error);
            return false;
        }
    }

    private static final class SurfaceMaskDrawable extends Drawable {
        private final Drawable delegate;
        private final Path mask;

        SurfaceMaskDrawable(Drawable delegate, Path mask) {
            this.delegate = delegate;
            this.mask = mask;
        }

        @Override public void draw(Canvas canvas) {
            Rect saved = new Rect(delegate.getBounds());
            int state = canvas.save();
            try {
                canvas.clipPath(mask);
                delegate.setBounds(getBounds());
                delegate.draw(canvas);
            } finally {
                delegate.setBounds(saved);
                canvas.restoreToCount(state);
            }
        }

        @Override public void setAlpha(int alpha) { delegate.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) {
            delegate.setColorFilter(filter);
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return delegate.getIntrinsicWidth(); }
        @Override public int getIntrinsicHeight() { return delegate.getIntrinsicHeight(); }
    }

    private static final java.util.HashSet<String> LOGGED = new java.util.HashSet<>();
    private static void logPreparedOnce(String path) {
        synchronized (LOGGED) {
            if (LOGGED.add("prepared-" + path))
                MainHook.log(TAG + " " + path + " cached alpha mask active");
        }
    }
    private static void logFailureOnce(String path, Throwable error) {
        synchronized (LOGGED) {
            if (LOGGED.add("failed-" + path))
                MainHook.log(TAG + " " + path + " skipped: " + error);
        }
    }

    private LauncherIconGlassCachedAsset() {}
}
