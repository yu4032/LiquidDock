package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;
import java.util.WeakHashMap;

/** Clips only the icon part of TextView's draw, leaving Launcher badges and labels alone. */
final class LauncherIconGlassClip {
    private static final WeakHashMap<View, Cache> CACHE = new WeakHashMap<>();
    private static volatile LiquidDockConfig.Glass glass;
    private static volatile float workstationRadiusDp;
    private static final class Cache {
        float left, top, right, bottom, gl, gt, gr, gb, radius, hostWidth;
        boolean continuous;
        final Path cutout = new Path();
        boolean matches(LauncherGlassIconGeometry.Bounds i, LauncherIconGlassClipGeometry.Result g,
                        boolean c, float width) {
            return left == i.left && top == i.top && right == i.right && bottom == i.bottom
                    && gl == g.left && gt == g.top && gr == g.right && gb == g.bottom
                    && radius == g.radius && continuous == c && hostWidth == width;
        }
        void update(LauncherGlassIconGeometry.Bounds i, LauncherIconGlassClipGeometry.Result g,
                    boolean c, float width) {
            left=i.left; top=i.top; right=i.right; bottom=i.bottom;
            gl=g.left; gt=g.top; gr=g.right; gb=g.bottom; radius=g.radius; continuous=c;
            hostWidth = width;
            cutout.reset();
            // TextView's compound drawable may render outside its reported bounds (shadow,
            // mask, OEM edge pixels). Subtract the glass from the entire icon row rather
            // than only from the nominal drawable rectangle: the latter leaves a straight
            // vertical seam at the old icon edge, especially after dropping into Dock.
            // The label lies below this row, and ShortcutIcon's separate badge drawing
            // stays outside the hooked TextView.onDraw() call.
            float[] row = LauncherIconGlassClipGeometry.iconRow(width, top, bottom);
            cutout.addRect(row[0], row[1], row[2], row[3], Path.Direction.CW);
            Path shape = new Path();
            LauncherIconGlassShapePath.build(shape, gl, gt, gr, gb, radius, c);
            cutout.op(shape, Path.Op.DIFFERENCE);
        }
    }
    static void configure(LiquidDockConfig.Glass config) { glass = config; }
    static void workstation(LiquidDockConfig.Workstation config) {
        workstationRadiusDp = config != null ? config.dockIconGlassCornerRadius : 0f;
    }
    static float workstationRadiusDp() { return workstationRadiusDp; }
    static boolean hasGlass(View view) {
        if (view == null || !GlassRuntimeState.isAnyIconEnabled()) return false;
        if (DockGlassItemRegistry.contains(view)) return true;
        LauncherGlassStaticNode node = LauncherGlassStaticNode.find(view);
        return node != null && node.kind() == LauncherGlassDragState.Kind.ICON;
    }

    /** Same footprint and corner radius as the stationary icon's mask. */
    static LauncherIconGlassClipGeometry.Result resolveShape(View view,
            LiquidDockConfig.Glass config, LauncherGlassIconGeometry.Bounds icon,
            boolean dock) {
        if (!(view instanceof TextView) || config == null || icon == null) return null;
        float density = view.getResources().getDisplayMetrics().density;
        float radiusDp = config.iconStyle.cornerRadiusDp;
        if (dock) {
            radiusDp = WorkstationDockIconRadiusPolicy.resolve(radiusDp,
                    workstationRadiusDp, density, MainHook.isWorkstationMode());
        }
        Drawable[] drawables = ((TextView) view).getCompoundDrawables();
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
        return LauncherIconGlassClipGeometry.resolve(
                icon.left, icon.top, icon.right, icon.bottom,
                config.iconStyle.sizeOffsetDp * density, radiusDp * density, nativeRadius);
    }

    static float targetRadiusPx(View source) {
        if (!hasGlass(source)) return Float.NaN;
        LiquidDockConfig.Glass config = glass;
        LauncherGlassIconGeometry.Bounds icon = LauncherGlassIconGeometry.resolve(source);
        LauncherIconGlassClipGeometry.Result shape = resolveShape(source, config, icon,
                DockGlassItemRegistry.contains(source));
        return shape != null ? shape.radius : Float.NaN;
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
                // The native FloatingIcon owns the moving pixels while its proxy is active.
                // The stationary source is hidden and need not pay for a GPU clip every frame.
                if (dock ? DockGlassItemRegistry.isProxyActive(view)
                        : node.isLaunchProxyActive()) return chain.proceed(args);
                LauncherGlassIconGeometry.Bounds icon = LauncherGlassIconGeometry.resolve(view);
                if (icon == null) return chain.proceed(args);
                LauncherIconGlassClipGeometry.Result shape = resolveShape(view, config, icon, dock);
                if (shape == null) return chain.proceed(args);
                Cache cache = CACHE.get(view);
                if (cache == null) { cache = new Cache(); CACHE.put(view, cache); }
                if (!cache.matches(icon, shape, config.continuousCorners, view.getWidth()))
                    cache.update(icon, shape, config.continuousCorners, view.getWidth());
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
