package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.view.View;
import android.widget.TextView;
import com.hellovoid.prismal.PrismalContinuousCorners;
import java.util.WeakHashMap;

/** Clips only the icon part of TextView's draw, leaving Launcher badges and labels alone. */
final class LauncherIconGlassClip {
    private static final WeakHashMap<View, Cache> CACHE = new WeakHashMap<>();
    private static volatile LiquidDockConfig.Glass glass;
    private static volatile float workstationRadiusDp;
    private static final class Cache {
        float left, top, right, bottom, gl, gt, gr, gb, radius, viewWidth;
        boolean continuous;
        final Path cutout = new Path();

        // Geometry of regular immutable icon artwork is stable between view draws.
        // In particular, avoid allocating Outline + Rect in resolveAutoRadius().
        Drawable sourceDrawable;
        LiquidDockConfig.Glass sourceConfig;
        int sourceHostHeight, sourceScrollX, sourceScrollY, sourcePaddingTop;
        int sourceCompoundLeft, sourceCompoundRight;
        int sourceDrawableLeft, sourceDrawableTop, sourceDrawableRight, sourceDrawableBottom;
        int sourceIntrinsicWidth, sourceIntrinsicHeight;
        float sourceDensity, sourceWorkstationRadius;
        boolean sourceDock, sourceWorkstationMode;
        boolean sourceValid;

        boolean matchesSource(TextView view, Drawable drawable,
                              LiquidDockConfig.Glass config, boolean dock,
                              boolean workstationMode, float workstationRadius) {
            // Fancy/animated OEM Drawable implementations can mutate their
            // outline without changing identity or bounds; don't cache those.
            if (!sourceValid || !simpleDrawable(drawable)
                    || drawable != sourceDrawable || config != sourceConfig
                    || dock != sourceDock
                    || (dock && (workstationMode != sourceWorkstationMode
                        || workstationRadius != sourceWorkstationRadius))
                    || view.getWidth() != viewWidth || view.getHeight() != sourceHostHeight
                    || view.getScrollX() != sourceScrollX || view.getScrollY() != sourceScrollY
                    || view.getPaddingTop() != sourcePaddingTop
                    || view.getCompoundPaddingLeft() != sourceCompoundLeft
                    || view.getCompoundPaddingRight() != sourceCompoundRight
                    || view.getResources().getDisplayMetrics().density != sourceDensity) {
                return false;
            }
            Rect b = drawable.getBounds();
            return b.left == sourceDrawableLeft && b.top == sourceDrawableTop
                    && b.right == sourceDrawableRight && b.bottom == sourceDrawableBottom
                    && drawable.getIntrinsicWidth() == sourceIntrinsicWidth
                    && drawable.getIntrinsicHeight() == sourceIntrinsicHeight;
        }

        void rememberSource(TextView view, Drawable drawable,
                            LiquidDockConfig.Glass config, boolean dock,
                            boolean workstationMode, float workstationRadius) {
            sourceValid = simpleDrawable(drawable);
            if (!sourceValid) return;
            sourceDrawable = drawable;
            sourceConfig = config;
            sourceDock = dock;
            sourceWorkstationMode = workstationMode;
            sourceWorkstationRadius = workstationRadius;
            sourceHostHeight = view.getHeight();
            sourceScrollX = view.getScrollX();
            sourceScrollY = view.getScrollY();
            sourcePaddingTop = view.getPaddingTop();
            sourceCompoundLeft = view.getCompoundPaddingLeft();
            sourceCompoundRight = view.getCompoundPaddingRight();
            sourceDensity = view.getResources().getDisplayMetrics().density;
            Rect b = drawable.getBounds();
            sourceDrawableLeft = b.left;
            sourceDrawableTop = b.top;
            sourceDrawableRight = b.right;
            sourceDrawableBottom = b.bottom;
            sourceIntrinsicWidth = drawable.getIntrinsicWidth();
            sourceIntrinsicHeight = drawable.getIntrinsicHeight();
        }

        private static boolean simpleDrawable(Drawable drawable) {
            // Only framework bitmaps and framework adaptive masks are known to
            // have geometry independent of runtime animated content.
            return drawable instanceof BitmapDrawable
                    || drawable instanceof AdaptiveIconDrawable;
        }

        boolean matches(LauncherGlassIconGeometry.Bounds i, LauncherIconGlassClipGeometry.Result g,
                        boolean c, float width) {
            return viewWidth == width && left == i.left && top == i.top && right == i.right && bottom == i.bottom
                    && gl == g.left && gt == g.top && gr == g.right && gb == g.bottom
                    && radius == g.radius && continuous == c;
        }
        void update(LauncherGlassIconGeometry.Bounds i, LauncherIconGlassClipGeometry.Result g,
                    boolean c, float width) {
            viewWidth = width;
            left=i.left; top=i.top; right=i.right; bottom=i.bottom;
            gl=g.left; gt=g.top; gr=g.right; gb=g.bottom; radius=g.radius; continuous=c;
            cutout.reset();
            float[] row = LauncherIconGlassClipGeometry.cutoutRow(width, top, bottom);
            cutout.addRect(row[0], row[1], row[2], row[3], Path.Direction.CW);
            Path shape = new Path();
            if (c) {
                float[] points = PrismalContinuousCorners.contour(gr-gl, gb-gt, radius);
                if (points.length >= 2) {
                    shape.moveTo(gl + points[0], gt + points[1]);
                    for (int n=2; n<points.length; n+=2) shape.lineTo(gl+points[n], gt+points[n+1]);
                    shape.close();
                }
            } else {
                shape.addRoundRect(new RectF(gl, gt, gr, gb), radius, radius, Path.Direction.CW);
            }
            cutout.op(shape, Path.Op.DIFFERENCE);
        }
    }
    static void configure(LiquidDockConfig.Glass config) { glass = config; }
    static void workstation(LiquidDockConfig.Workstation config) {
        workstationRadiusDp = config != null ? config.dockIconGlassCornerRadius : 0f;
    }
    static void install(ClassLoader loader) {
        try {
            Class<?> shortcutClass = Class.forName("com.miui.home.launcher.ShortcutIcon", false, loader);
            HookUtil.hook(HookUtil.findMethodExact(TextView.class, "onDraw", new Class<?>[]{Canvas.class}), chain -> {
                Object owner = chain.getThisObject();
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (!shortcutClass.isInstance(owner) || args.length == 0 || !(args[0] instanceof Canvas))
                    return chain.proceed(args);
                TextView view = (TextView) owner;
                LiquidDockConfig.Glass config = glass;
                boolean dock = DockGlassItemRegistry.contains(view);
                LauncherGlassStaticNode node = dock ? null : LauncherGlassStaticNode.find(view);
                if (config == null || !GlassRuntimeState.isAnyIconEnabled()
                        || (!dock && (node == null || node.kind() != LauncherGlassDragState.Kind.ICON)))
                    return chain.proceed(args);
                Drawable[] drawables = view.getCompoundDrawables();
                Drawable drawable = drawables.length > 1 ? drawables[1] : null;
                Cache cache = CACHE.get(view);
                boolean workstationMode = dock && MainHook.isWorkstationMode();
                if (cache == null || !cache.matchesSource(view, drawable, config,
                        dock, workstationMode, workstationRadiusDp)) {
                    LauncherGlassIconGeometry.Bounds icon = LauncherGlassIconGeometry.resolve(view);
                    if (icon == null) return chain.proceed(args);
                    float density = view.getResources().getDisplayMetrics().density;
                    float radiusDp = config.iconStyle.cornerRadiusDp;
                    if (dock) radiusDp = WorkstationDockIconRadiusPolicy.resolve(radiusDp,
                            workstationRadiusDp, density, workstationMode);
                    float min = Math.min(icon.width(), icon.height());
                    float nativeRadius = LauncherGlassIconShapeResolver.resolveAutoRadius(
                            drawable, min, min, min * .22f);
                    if (dock && radiusDp <= 0f) {
                        float[] bounds = LauncherGlassBoundsPolicy.apply(icon.left, icon.top,
                                icon.right, icon.bottom, config.iconStyle.sizeOffsetDp * density);
                        float width = bounds[2] - bounds[0], height = bounds[3] - bounds[1];
                        nativeRadius = LauncherGlassIconShapeResolver.resolveAutoRadius(
                                drawable, width, height, Math.min(width, height) * .22f);
                    }
                    LauncherIconGlassClipGeometry.Result shape =
                            LauncherIconGlassClipGeometry.resolve(
                                    icon.left, icon.top, icon.right, icon.bottom,
                                    config.iconStyle.sizeOffsetDp * density,
                                    radiusDp * density, nativeRadius);
                    if (cache == null) {
                        cache = new Cache();
                        CACHE.put(view, cache);
                    }
                    if (!cache.matches(icon, shape, config.continuousCorners, view.getWidth())) {
                        cache.update(icon, shape, config.continuousCorners, view.getWidth());
                    }
                    cache.rememberSource(view, drawable, config, dock,
                            workstationMode, workstationRadiusDp);
                }
                Canvas canvas = (Canvas) args[0];
                int save = canvas.save();
                try {
                    canvas.clipOutPath(cache.cutout);
                    return chain.proceed(args);
                } finally { canvas.restoreToCount(save); }
            });
            MainHook.log("[DC][IconGlassClip] installed");
        } catch (Throwable error) {
            MainHook.log("[DC][IconGlassClip] unavailable: " + error);
        }
    }
    private LauncherIconGlassClip() {}
}
