package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;

import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.prismal.PrismalContinuousCorners;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Initializes the OEM Dock's own drawable/outline before its first displayed frame.
 * Never clips the Dock parent, replaces its blur renderer or interferes with grid layout.
 * Continuous contours on native materials are optional; the first-frame fix is independent.
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
        int count = hookDock(loader, HOTSEATS + "HotSeatsListContentMiuiXBlurBackground",
                radiusMethods);
        count += hookDock(loader, HOTSEATS + "HotSeatsListContentBlurBackground2",
                radiusMethods);
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
                    if (view != null && continuous) {
                        DockState state = DOCKS.get(view);
                        if (state != null && !WorkstationRuntimeState.isActive()) {
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
        if (state != null) return;
        state = new DockState();
        DOCKS.put(view, state);
        // The constructor can finish before either size or OEM Drawable is initialized.
        // The first pre-draw is the last safe boundary before rendering the visible frame.
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View attached) {
                installFirstDrawProbe(attached);
            }
            @Override public void onViewDetachedFromWindow(View detached) {}
        });
        if (view.isAttachedToWindow()) installFirstDrawProbe(view);
        if (continuous) ensureOutline(view, state);
    }

    private static void installFirstDrawProbe(View view) {
        DockState state = DOCKS.get(view);
        if (state == null || state.firstFrameChecked || state.probe != null) return;
        ViewTreeObserver observer = view.getViewTreeObserver();
        if (!observer.isAlive()) return;
        ViewTreeObserver.OnPreDrawListener probe = new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                DockState current = DOCKS.get(view);
                if (current == null) return true;
                if (WorkstationRuntimeState.isActive() || GlassRuntimeState.isEnabled()
                        && MiuixGlassHook.isBoundTo(view)) {
                    current.firstFrameChecked = true;
                } else if (view.getWidth() > 0 && view.getHeight() > 0) {
                    synchronizeFirstDrawable(view, current);
                    current.firstFrameChecked = true;
                    if (continuous) {
                        ensureOutline(view, current);
                        view.invalidateOutline();
                    }
                }
                if (current.firstFrameChecked) {
                    ViewTreeObserver tree = view.getViewTreeObserver();
                    if (tree.isAlive()) tree.removeOnPreDrawListener(this);
                    current.probe = null;
                }
                return true;
            }
        };
        state.probe = probe;
        observer.addOnPreDrawListener(probe);
    }

    private static void synchronizeFirstDrawable(View view, DockState state) {
        GradientDrawable background = nativeDrawable(view);
        float nativeRadius = MiuixFolderGlassHook.readMaterialRadius(view);
        if (background != null) {
            float drawableRadius = background.getCornerRadius();
            if (background.getCornerRadii() != null) return; // Respect asymmetric OEM geometry.
            if (!(nativeRadius > .5f) && drawableRadius > .5f) nativeRadius = drawableRadius;
        }
        float radius = nativeRadius > .5f ? nativeRadius
                : NativeDockCornerPolicy.provisionalRadius(view.getWidth(), view.getHeight(),
                        WorkstationRuntimeState.isActive());
        if (!(radius > .5f) || !Float.isFinite(radius)) return;
        state.firstRadius = radius;
        if (background != null && background.getCornerRadius() <= .5f) {
            background.mutate();
            background.setCornerRadius(radius);
            view.invalidateOutline();
            MainHook.log(TAG + " first native background radius initialized="
                    + radius + " size=" + view.getWidth() + "x" + view.getHeight());
        } else if (background == null) {
            MainHook.log(TAG + " first native background has no GradientDrawable; "
                    + "outline-only radius=" + radius);
        }
    }

    private static GradientDrawable nativeDrawable(View view) {
        for (Class<?> c = view.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField("mBackground");
                field.setAccessible(true);
                Object value = field.get(view);
                if (value instanceof GradientDrawable) return (GradientDrawable) value;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable error) {
                break;
            }
        }
        Drawable background = view.getBackground();
        return background instanceof GradientDrawable ? (GradientDrawable) background : null;
    }

    private static void ensureOutline(View view, DockState state) {
        // Stock mode must keep the native provider and its native animation untouched.
        if (!continuous || WorkstationRuntimeState.isActive()
                || MiuixGlassHook.isBoundTo(view)
                || LauncherGlassStaticNode.find(view) != null) return;
        if (view.getOutlineProvider() instanceof NativeOutline) return;
        ViewOutlineProvider original = view.getOutlineProvider();
        view.setOutlineProvider(new NativeOutline(
                original != null ? original : ViewOutlineProvider.BACKGROUND, state));
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
                Object result = chain.proceed(arguments);
                if (arguments.length > 0 && arguments[0] instanceof View
                        && (!"setMaMlBlurIfSupported".equals(name)
                            || (arguments.length > 1 && Boolean.TRUE.equals(arguments[1])))) {
                    applyNative((View) arguments[0]);
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
        float firstRadius = Float.NaN;
        boolean firstFrameChecked;
        ViewTreeObserver.OnPreDrawListener probe;
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
            if (!output.getRect(bounds) || bounds.isEmpty()) {
                if (dock == null || !output.isEmpty()
                        || view.getWidth() <= 0 || view.getHeight() <= 0) return;
                bounds.set(0, 0, view.getWidth(), view.getHeight());
            }
            float radius = output.getRadius();
            if (!(radius > .5f)) radius = MiuixFolderGlassHook.readMaterialRadius(view);
            if (!(radius > .5f) && dock != null) radius = dock.firstRadius;
            if (!(radius > .5f) || !Float.isFinite(radius)) {
                // A missing OEM radius is not an instruction to draw a rectangle.
                // In particular, never replace a future taskbar outline with a zero-radius path.
                return;
            }
            radius = Math.min(radius, .5f * Math.min(bounds.width(), bounds.height()));
            float alpha = output.getAlpha();
            if (!bounds.equals(cached) || cachedRadius != radius) {
                float[] points = PrismalContinuousCorners.contour(
                        bounds.width(), bounds.height(), radius);
                if (points.length < 8) return;
                path.rewind();
                path.moveTo(bounds.left + points[0], bounds.top + points[1]);
                for (int i = 2; i < points.length; i += 2)
                    path.lineTo(bounds.left + points[i], bounds.top + points[i + 1]);
                path.close();
                cached.set(bounds);
                cachedRadius = radius;
            }
            output.setPath(path);
            output.setAlpha(alpha);
        }
    }
}
