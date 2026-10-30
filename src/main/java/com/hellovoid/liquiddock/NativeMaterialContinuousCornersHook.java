package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.prismal.PrismalContinuousCorners;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Changes only the OEM material outline, not its blur, Drawable, content or animations.
 * Dock backgrounds receive a provider during construction, before the first blur frame.
 * The same iPadOS geometry is also available for native widget/folder materials.
 */
final class NativeMaterialContinuousCornersHook {
    private static final String TAG = "[DC][NativeCorners]";
    private static final String HOTSEATS = "com.miui.home.launcher.hotseats.";
    private static final Map<View, DockState> DOCKS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;
    private static boolean continuous;

    private NativeMaterialContinuousCornersHook() {}

    static synchronized void install(ClassLoader loader, boolean enabled) {
        if (installed || !enabled || loader == null) return;
        continuous = ConfigReader.load().b(ConfigSchema.Glass.CONTINUOUS_CORNERS.name(),
                ConfigSchema.Glass.CONTINUOUS_CORNERS.runtimeFallback());
        Set<Method> radiusMethods = new HashSet<>();
        int count = 0;
        count += hookDock(loader, HOTSEATS + "HotSeatsListContentMiuiXBlurBackground", radiusMethods);
        count += hookDock(loader, HOTSEATS + "HotSeatsListContentBlurBackground2", radiusMethods);
        if (continuous) count += hookNativeWidgetsAndFolders(loader);
        installed = count > 0;
        MainHook.log(TAG + " hooks=" + count + " continuous=" + continuous);
    }

    static boolean continuousEnabled() {
        return continuous;
    }

    private static int hookDock(ClassLoader loader, String name, Set<Method> hooked) {
        try {
            Class<?> type = Class.forName(name, false, loader);
            int count = 0;
            for (Constructor<?> ctor : type.getDeclaredConstructors()) {
                try {
                    HookUtil.hook(ctor, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (chain.getThisObject() instanceof View) {
                            observeDock((View) chain.getThisObject());
                        }
                        return result;
                    });
                    count++;
                } catch (Throwable error) {
                    MainHook.log(TAG + " constructor " + name + ": " + error);
                }
            }
            Method radius = findRadiusMethod(type);
            if (radius != null && hooked.add(radius)) {
                HookUtil.hook(radius, chain -> {
                    View view = chain.getThisObject() instanceof View
                            ? (View) chain.getThisObject() : null;
                    if (view != null) observeDock(view);
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (view != null) {
                        DockState state = DOCKS.get(view);
                        if (state != null) {
                            state.radiusCommitted = true;
                            // OEM can replace its provider during a radius update.
                            ensureOutline(view, state);
                            view.invalidateOutline();
                        }
                    }
                    return result;
                });
                count++;
            }
            return count;
        } catch (Throwable error) {
            MainHook.log(TAG + " Dock unavailable " + name + ": " + error);
            return 0;
        }
    }

    private static Method findRadiusMethod(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod("setBackgroundRadius", float.class);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    private static void observeDock(View view) {
        DockState state = DOCKS.get(view);
        if (state == null) {
            state = new DockState();
            DOCKS.put(view, state);
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                    DockState current = DOCKS.get(v);
                    if (current != null) {
                        ensureOutline(v, current);
                        v.invalidateOutline();
                    }
                }
                @Override public void onViewDetachedFromWindow(View v) {}
            });
            view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (r - l == or - ol && b - t == ob - ot) return;
                DockState current = DOCKS.get(v);
                if (current == null) return;
                ensureOutline(v, current);
                v.invalidateOutline();
            });
        }
        ensureOutline(view, state);
    }

    private static void ensureOutline(View view, DockState state) {
        if (MiuixGlassHook.isBoundTo(view) || LauncherGlassStaticNode.find(view) != null) return;
        if (view.getOutlineProvider() instanceof NativeOutline) return;
        ViewOutlineProvider original = view.getOutlineProvider();
        state.original = original != null ? original : ViewOutlineProvider.BACKGROUND;
        view.setOutlineProvider(new NativeOutline(state.original, state));
        view.invalidateOutline();
    }

    private static int hookNativeWidgetsAndFolders(ClassLoader loader) {
        int count = 0;
        try {
            Class<?> blur = Class.forName("com.miui.home.launcher.common.BlurUtilities", false, loader);
            count += hookInitializer(blur, "setWidgetBlurIfSupported", View.class);
            count += hookInitializer(blur, "setFolderIconBlur",
                    View.class, int.class, int.class, int.class, int.class, int.class,
                    int.class, int.class, int.class, int.class, int.class);
            try {
                Class<?> maml = Class.forName("com.miui.maml.component.MamlView", false, loader);
                count += hookInitializer(blur, "setMaMlBlurIfSupported", maml, boolean.class);
            } catch (Throwable error) {
                MainHook.log(TAG + " MAML unavailable: " + error);
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " BlurUtilities unavailable: " + error);
        }
        return count;
    }

    private static int hookInitializer(Class<?> owner, String name, Class<?>... args) {
        try {
            Method method = HookUtil.findMethodExact(owner, name, args);
            HookUtil.hook(method, chain -> {
                Object[] arguments = chain.getArgs().toArray(new Object[0]);
                View material = arguments.length > 0 && arguments[0] instanceof View
                        ? (View) arguments[0] : null;
                // Prepare the silhouette before the vendor may submit background blur.
                if (material != null) applyNative(material);
                Object result = chain.proceed(arguments);
                if (material != null && (!"setMaMlBlurIfSupported".equals(name)
                        || (arguments.length > 1 && Boolean.TRUE.equals(arguments[1])))) {
                    applyNative(material);
                }
                return result;
            });
            return 1;
        } catch (Throwable error) {
            MainHook.log(TAG + " initializer unavailable " + name + ": " + error);
            return 0;
        }
    }

    private static void applyNative(View view) {
        if (!continuous || view == null || MiuixGlassHook.isBoundTo(view)
                || LauncherGlassStaticNode.find(view) != null
                || view.getOutlineProvider() instanceof NativeOutline) return;
        ViewOutlineProvider current = view.getOutlineProvider();
        if (current == null && view.getBackground() == null
                && !(MiuixFolderGlassHook.readMaterialRadius(view) > 0f)) return;
        view.setOutlineProvider(new NativeOutline(
                current != null ? current : ViewOutlineProvider.BACKGROUND, null));
        view.invalidateOutline();
    }

    private static final class DockState {
        ViewOutlineProvider original;
        boolean radiusCommitted;
    }

    private static final class NativeOutline extends ViewOutlineProvider {
        private final ViewOutlineProvider original;
        private final DockState dock;
        private final Rect bounds = new Rect();
        private final Path path = new Path();
        private final Rect cached = new Rect();
        private float cachedRadius = Float.NaN;

        NativeOutline(ViewOutlineProvider original, DockState dock) {
            this.original = original;
            this.dock = dock;
        }

        @Override public void getOutline(View view, Outline output) {
            original.getOutline(view, output);
            boolean hasRect = output.getRect(bounds) && !bounds.isEmpty();
            if (!hasRect) {
                if (dock == null || !output.isEmpty()
                        || view.getWidth() <= 0 || view.getHeight() <= 0) return;
                bounds.set(0, 0, view.getWidth(), view.getHeight());
            }
            float radius = output.getRadius();
            if (!(radius > 0f)) {
                radius = MiuixFolderGlassHook.readMaterialRadius(view);
            }
            if (dock != null && !(radius > 0f) && !dock.radiusCommitted
                    && !WorkstationRuntimeState.isActive()) {
                // OEM Dock often exposes no radius until after the first blur transaction.
                radius = bounds.height() * .5f;
            }
            if (!Float.isFinite(radius)) return;
            radius = Math.max(0f, Math.min(radius,
                    .5f * Math.min(bounds.width(), bounds.height())));
            if (radius <= .5f) {
                if (dock != null) output.setRoundRect(bounds, 0f);
                return;
            }
            float alpha = output.getAlpha();
            if (!continuous) {
                if (dock != null) {
                    output.setRoundRect(bounds, radius);
                    output.setAlpha(alpha);
                }
                return;
            }
            if (!bounds.equals(cached) || cachedRadius != radius) {
                float[] points = PrismalContinuousCorners.contour(
                        bounds.width(), bounds.height(), radius);
                if (points.length < 8) return;
                path.rewind();
                path.moveTo(bounds.left + points[0], bounds.top + points[1]);
                for (int i = 2; i < points.length; i += 2) {
                    path.lineTo(bounds.left + points[i], bounds.top + points[i + 1]);
                }
                path.close();
                cached.set(bounds);
                cachedRadius = radius;
            }
            output.setPath(path);
            output.setAlpha(alpha);
        }
    }
}
