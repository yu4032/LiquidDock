package com.hellovoid.liquiddock;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.TextView;

import com.hellovoid.prismal.PrismalContinuousCorners;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Bake the icon's glass outline into a display-only bitmap when Launcher binds
 * its drawable. The original mIconDrawable stays untouched for native gestures,
 * drag snapshots, animation and shortcut visibility. No draw/onDraw hook.
 */
final class LauncherIconGlassClip {
    private static final String TAG = "[DC][IconGlassClip]";
    private static final WeakHashMap<TextView, Cache> CACHE = new WeakHashMap<>();
    private static volatile LiquidDockConfig.Glass glass;
    private static volatile float workstationRadiusDp;
    private static final HashSet<String> SOURCE_LOGGED = new HashSet<>();

    private static final class Cache {
        WeakReference<Drawable> original = new WeakReference<>(null);
        WeakReference<BitmapDrawable> display = new WeakReference<>(null);
        float width, height, left, top, right, bottom, radius;
        boolean continuous;
        float offsetDp, cornerDp;
        int hostWidth, hostHeight, baseIconSize, bitmapGeneration;
        int paddingTop, paddingLeft, paddingRight;
        boolean dock, workstationMode;
        float workstationRadius, density;

        boolean matchesBinding(TextView host, Drawable source,
                               LiquidDockConfig.Glass latest, boolean dock,
                               boolean workstationMode, int iconSize) {
            if (original.get() != source || display.get() == null
                    || this.dock != dock || this.workstationMode != workstationMode
                    || (dock && this.workstationRadius != workstationRadiusDp)
                    || this.baseIconSize != iconSize
                    || this.hostWidth != host.getWidth()
                    || this.hostHeight != host.getHeight()
                    || this.paddingTop != host.getPaddingTop()
                    || this.paddingLeft != host.getCompoundPaddingLeft()
                    || this.paddingRight != host.getCompoundPaddingRight()
                    || this.density != host.getResources().getDisplayMetrics().density
                    || this.offsetDp != latest.iconStyle.sizeOffsetDp
                    || this.cornerDp != latest.iconStyle.cornerRadiusDp
                    || this.continuous != latest.continuousCorners) return false;
            if (source instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) source).getBitmap();
                return bitmap != null && bitmapGeneration == bitmap.getGenerationId();
            }
            return true;
        }

        void set(Drawable original, BitmapDrawable display,
                 LauncherIconGlassClipGeometry.Result shape,
                 float width, float height, boolean continuous,
                 LiquidDockConfig.Glass config, TextView host,
                 boolean dock, boolean workstationMode, int iconSize) {
            this.original = new WeakReference<>(original);
            this.display = new WeakReference<>(display);
            this.left = shape.left;
            this.top = shape.top;
            this.right = shape.right;
            this.bottom = shape.bottom;
            this.radius = shape.radius;
            this.width = width;
            this.height = height;
            this.continuous = continuous;
            this.offsetDp = config.iconStyle.sizeOffsetDp;
            this.cornerDp = config.iconStyle.cornerRadiusDp;
            this.hostWidth = host.getWidth();
            this.hostHeight = host.getHeight();
            this.paddingTop = host.getPaddingTop();
            this.paddingLeft = host.getCompoundPaddingLeft();
            this.paddingRight = host.getCompoundPaddingRight();
            this.density = host.getResources().getDisplayMetrics().density;
            this.dock = dock;
            this.workstationMode = workstationMode;
            this.workstationRadius = workstationRadiusDp;
            this.baseIconSize = iconSize;
            Bitmap bitmap = original instanceof BitmapDrawable
                    ? ((BitmapDrawable) original).getBitmap() : null;
            this.bitmapGeneration = bitmap != null ? bitmap.getGenerationId() : -1;
        }
    }

    static void configure(LiquidDockConfig.Glass config) {
        LiquidDockConfig.Glass previous = glass;
        glass = config;
        if (sameGeometry(previous, config)) return;
        refreshAll();
    }

    private static boolean sameGeometry(LiquidDockConfig.Glass before,
                                        LiquidDockConfig.Glass after) {
        if (before == after) return true;
        if (before == null || after == null) return false;
        return before.continuousCorners == after.continuousCorners
                && before.iconStyle.sizeOffsetDp == after.iconStyle.sizeOffsetDp
                && before.iconStyle.cornerRadiusDp == after.iconStyle.cornerRadiusDp;
    }

    static void workstation(LiquidDockConfig.Workstation config) {
        float next = config != null ? config.dockIconGlassCornerRadius : 0f;
        if (workstationRadiusDp == next) return;
        workstationRadiusDp = next;
        refreshAll();
    }

    /** Called only on settings/policy changes, not on any animation frame. */
    static void refreshAll() {
        for (TextView host : new ArrayList<>(CACHE.keySet())) {
            if (host == null) continue;
            Cache cache = CACHE.get(host);
            if (cache == null) continue;
            cache.offsetDp = Float.NaN; // invalidate geometry-only cache
            host.post(() -> {
                if (host.isAttachedToWindow()) {
                    HookUtil.tryInvoke(host, "updateIconDrawable");
                }
            });
        }
    }

    static void refreshHost(View host) {
        if (host instanceof TextView) {
            Cache cache = CACHE.get((TextView) host);
            if (cache != null) cache.offsetDp = Float.NaN;
            host.post(() -> {
                if (host.isAttachedToWindow()) {
                    HookUtil.tryInvoke(host, "updateIconDrawable");
                }
            });
        }
    }

    static void install(ClassLoader loader) {
        try {
            Class<?> shortcut = Class.forName("com.miui.home.launcher.ShortcutIcon", false, loader);

            // OEM Launcher 4.50: applyIconDrawable() is used by bind, measure,
            // setIconVisibility(0) and animation handoff. Never touch mIconDrawable.
            HookUtil.hook(HookUtil.findMethodExact(shortcut, "applyIconDrawable",
                    new Class<?>[]{Drawable.class}), chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                if (!(chain.getThisObject() instanceof TextView)) return result;
                TextView host = (TextView) chain.getThisObject();
                Drawable original = args.length == 1 && args[0] instanceof Drawable
                        ? (Drawable) args[0] : null;
                if (original == null) return result;

                // The OEM transparent placeholder is installed during app transitions.
                // Replacing it would change the identity-based visibility contract.
                if (!eligible(host)) return result;
                if (!safeStaticDrawable(original, 0)) {
                    logSourceOnce(original, "skip");
                    return result;
                }
                try {
                    installCachedDrawable(host, original);
                } catch (Throwable error) {
                    logSourceOnce(original, "error: " + error);
                }
                return result;
            });

            // Native press/spring gesture changes mIconDrawable bounds. Mirror
            // just its rectangle to the baked display bitmap; no clipping work.
            HookUtil.hook(HookUtil.findMethodExact(shortcut, "setIconDrawableScale",
                    new Class<?>[]{float.class}), chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                TextView host = (TextView) chain.getThisObject();
                Cache cache = CACHE.get(host);
                BitmapDrawable baked = cache != null ? cache.display.get() : null;
                if (baked != null && host.getCompoundDrawables()[1] == baked) {
                    Drawable nativeDrawable = cache.original.get();
                    if (nativeDrawable != null)
                        baked.setBounds(nativeDrawable.getBounds());
                }
                return result;
            });
            MainHook.log(TAG + " baked resource hooks installed (no onDraw clipping)");
        } catch (Throwable error) {
            MainHook.log(TAG + " unavailable: " + error);
        }
    }

    private static boolean eligible(TextView view) {
        if (glass == null || !GlassRuntimeState.isAnyIconEnabled()) return false;
        if (DockGlassItemRegistry.contains(view)) return true;
        LauncherGlassStaticNode node = LauncherGlassStaticNode.find(view);
        return node != null && node.kind() == LauncherGlassDragState.Kind.ICON;
    }

    /**
     * Xiaomi's icon cache returns LayerAdaptiveIconDrawable/BadgeDrawable as well
     * as framework bitmaps. An exact-class whitelist silently disabled clipping
     * for most OS3 icons. Check the content instead; never freeze live layers.
     */
    private static boolean safeStaticDrawable(Drawable drawable, int depth) {
        if (drawable == null || depth > 4 || drawable instanceof Animatable
                || (depth == 0 && drawable.getClass() == ColorDrawable.class)) return false;
        String name = drawable.getClass().getName();
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("fancy") || lower.contains("maml")
                || lower.contains("lottie") || lower.contains("animated")
                || lower.contains("dynamic")) return false;

        if (name.equals("com.miui.home.launcher.graphics.drawable.BadgeDrawable")) {
            // Work-profile badges are static decorations, but their image
            // source must not secretly be a live MAML/Fancy drawable.
            HookUtil.InvocationResult<Object> back =
                    HookUtil.tryInvoke(drawable, "getBackDrawable");
            HookUtil.InvocationResult<Object> badge =
                    HookUtil.tryInvoke(drawable, "getBadgeDrawable");
            if (!back.succeeded() || !(back.value() instanceof Drawable)
                    || !safeStaticDrawable((Drawable) back.value(), depth + 1)) return false;
            return !badge.succeeded() || badge.value() == null
                    || badge.value() instanceof Drawable
                    && safeStaticDrawable((Drawable) badge.value(), depth + 1);
        }
        if (name.equals("com.miui.home.launcher.graphics.drawable.LayerAdaptiveIconDrawable")) {
            HookUtil.InvocationResult<Object> background =
                    HookUtil.tryInvoke(drawable, "getBackgroundLayer");
            HookUtil.InvocationResult<Object> foreground =
                    HookUtil.tryInvoke(drawable, "getForegroundLayers");
            if (!background.succeeded() || !foreground.succeeded()
                    || !(foreground.value() instanceof List<?>)) return false;
            if (background.value() != null
                    && !safeLayer(background.value(), depth + 1)) return false;
            for (Object layer : (List<?>) foreground.value())
                if (!safeLayer(layer, depth + 1)) return false;
            return true;
        }
        // Other ordinary non-animated Drawables can be snapshotted once.
        // The OEM original stays alive for native app-return/drag animations.
        return true;
    }

    private static boolean safeLayer(Object layer, int depth) {
        if (layer == null) return true;
        HookUtil.InvocationResult<Object> current =
                HookUtil.tryInvoke(layer, "getCurrentShowDrawable");
        return current.succeeded() && current.value() instanceof Drawable
                && safeStaticDrawable((Drawable) current.value(), depth + 1);
    }

    private static void logSourceOnce(Drawable source, String status) {
        String key = status + ":" + source.getClass().getName();
        synchronized (SOURCE_LOGGED) {
            if (SOURCE_LOGGED.add(key)) {
                MainHook.log(TAG + " " + status + " source=" + source.getClass().getName());
            }
        }
    }

    private static void installCachedDrawable(TextView host, Drawable original) {
        LiquidDockConfig.Glass config = glass;
        if (config == null) return;
        boolean dock = DockGlassItemRegistry.contains(host);
        boolean workstationMode = dock && MainHook.isWorkstationMode();
        HookUtil.InvocationResult<Object> iconSizeResult =
                HookUtil.tryInvoke(host, "getIconSize");
        int iconSize = iconSizeResult.succeeded() && iconSizeResult.value() instanceof Number
                ? ((Number) iconSizeResult.value()).intValue() : 0;
        Cache cache = CACHE.get(host);
        if (cache != null && cache.matchesBinding(host, original, config,
                dock, workstationMode, iconSize)) {
            BitmapDrawable existing = cache.display.get();
            if (existing != null) {
                existing.setBounds(original.getBounds());
                replaceDisplayOnly(host, existing);
            }
            return;
        }

        Rect bounds = original.getBounds();
        int width = bounds.width();
        int height = bounds.height();
        if (width <= 0 || height <= 0 || width > 512 || height > 512) {
            logSourceOnce(original, "skip-bounds " + width + "x" + height);
            return;
        }
        LauncherGlassIconGeometry.Bounds icon = LauncherGlassIconGeometry.resolve(host);
        if (icon == null) {
            logSourceOnce(original, "skip-geometry");
            return;
        }
        float density = host.getResources().getDisplayMetrics().density;
        float radiusDp = config.iconStyle.cornerRadiusDp;
        if (dock) {
            radiusDp = WorkstationDockIconRadiusPolicy.resolve(
                    radiusDp, workstationRadiusDp, density, workstationMode);
        }
        float min = Math.min(icon.width(), icon.height());
        float nativeRadius = LauncherGlassIconShapeResolver.resolveAutoRadius(
                original, min, min, min * .22f);
        if (dock && radiusDp <= 0f) {
            float[] adjusted = LauncherGlassBoundsPolicy.apply(icon.left, icon.top,
                    icon.right, icon.bottom, config.iconStyle.sizeOffsetDp * density);
            float w = adjusted[2] - adjusted[0], h = adjusted[3] - adjusted[1];
            nativeRadius = LauncherGlassIconShapeResolver.resolveAutoRadius(
                    original, w, h, Math.min(w, h) * .22f);
        }
        LauncherIconGlassClipGeometry.Result shape = LauncherIconGlassClipGeometry.resolve(
                icon.left, icon.top, icon.right, icon.bottom,
                config.iconStyle.sizeOffsetDp * density, radiusDp * density, nativeRadius);

        // Translate the unchanged static glass outline from TextView to the
        // top compound Drawable's own local coordinate system.
        LauncherIconGlassClipGeometry.Result local =
                LauncherIconGlassClipGeometry.localize(shape, icon.left, icon.top);
        // This branch runs only when a source, silhouette, or geometry changed.
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Path outline = new Path();
        if (config.continuousCorners) {
            float[] xy = PrismalContinuousCorners.contour(local.right - local.left,
                    local.bottom - local.top, local.radius);
            if (xy.length < 2) return;
            outline.moveTo(local.left + xy[0], local.top + xy[1]);
            for (int i = 2; i < xy.length; i += 2)
                outline.lineTo(local.left + xy[i], local.top + xy[i + 1]);
            outline.close();
        } else {
            outline.addRoundRect(local.left, local.top, local.right, local.bottom,
                    local.radius, local.radius, Path.Direction.CW);
        }
        canvas.clipPath(outline);

        // One-time draw with the existing OEM tint/filter and native bounds.
        // No BitmapDrawable/AdaptiveIconDrawable state is modified.
        int save = canvas.save();
        try {
            canvas.translate(-bounds.left, -bounds.top);
            original.draw(canvas);
        } finally {
            canvas.restoreToCount(save);
        }

        BitmapDrawable baked = new BitmapDrawable(host.getResources(), bitmap);
        baked.setBounds(bounds);
        if (cache == null) {
            cache = new Cache();
            CACHE.put(host, cache);
        }
        cache.set(original, baked, local, width, height,
                config.continuousCorners, config, host,
                dock, workstationMode, iconSize);
        replaceDisplayOnly(host, baked);
        logSourceOnce(original, "baked");
    }

    private static void replaceDisplayOnly(TextView host, BitmapDrawable drawable) {
        Drawable[] current = host.getCompoundDrawables();
        if (current[1] == drawable) return;
        host.setCompoundDrawables(current[0], drawable, current[2], current[3]);
    }

    private LauncherIconGlassClip() {}
}
