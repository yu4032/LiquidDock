package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
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
        float left, top, right, bottom, gl, gt, gr, gb, radius;
        boolean continuous;
        final Path cutout = new Path();
        boolean matches(LauncherGlassIconGeometry.Bounds i, LauncherIconGlassClipGeometry.Result g,
                        boolean c) {
            return left == i.left && top == i.top && right == i.right && bottom == i.bottom
                    && gl == g.left && gt == g.top && gr == g.right && gb == g.bottom
                    && radius == g.radius && continuous == c;
        }
        void update(LauncherGlassIconGeometry.Bounds i, LauncherIconGlassClipGeometry.Result g,
                    boolean c) {
            left=i.left; top=i.top; right=i.right; bottom=i.bottom;
            gl=g.left; gt=g.top; gr=g.right; gb=g.bottom; radius=g.radius; continuous=c;
            cutout.reset();
            cutout.addRect(left, top, right, bottom, Path.Direction.CW);
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
                LauncherGlassIconGeometry.Bounds icon = LauncherGlassIconGeometry.resolve(view);
                if (icon == null) return chain.proceed(args);
                float density = view.getResources().getDisplayMetrics().density;
                float radiusDp = config.iconStyle.cornerRadiusDp;
                if (dock) radiusDp = WorkstationDockIconRadiusPolicy.resolve(radiusDp,
                        workstationRadiusDp, density, MainHook.isWorkstationMode());
                Drawable[] drawables = view.getCompoundDrawables();
                Drawable drawable = drawables.length > 1 ? drawables[1] : null;
                float min = Math.min(icon.width(), icon.height());
                float nativeRadius = LauncherGlassIconShapeResolver.resolveAutoRadius(
                        drawable, min, min, min * .22f);
                if (dock && radiusDp <= 0f) {
                    float[] b = LauncherGlassBoundsPolicy.apply(icon.left, icon.top, icon.right,
                            icon.bottom, config.iconStyle.sizeOffsetDp * density);
                    float width = b[2] - b[0], height = b[3] - b[1];
                    nativeRadius = LauncherGlassIconShapeResolver.resolveAutoRadius(
                            drawable, width, height, Math.min(width, height) * .22f);
                }
                LauncherIconGlassClipGeometry.Result shape = LauncherIconGlassClipGeometry.resolve(
                        icon.left, icon.top, icon.right, icon.bottom,
                        config.iconStyle.sizeOffsetDp * density, radiusDp * density, nativeRadius);
                Cache cache = CACHE.get(view);
                if (cache == null) { cache = new Cache(); CACHE.put(view, cache); }
                if (!cache.matches(icon, shape, config.continuousCorners))
                    cache.update(icon, shape, config.continuousCorners);
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
