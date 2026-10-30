package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;

import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.prismal.PrismalContinuousCorners;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
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
                    if (view != null) {
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
        state = new DockState(view);
        DOCKS.put(view, state);
        // This is the material View itself, never HotSeats or an icon-containing ancestor.
        // An OutlineProvider alone is metadata: the native RenderNode must be clipped.
        ensureOutline(view, state);
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View attached) {
                installFirstDrawProbe(attached);
            }
            @Override public void onViewDetachedFromWindow(View detached) {
                DockState current = DOCKS.get(detached);
                if (current != null) current.removeProbe(detached);
            }
        });
        if (view.isAttachedToWindow()) installFirstDrawProbe(view);
    }

    private static void installFirstDrawProbe(View view) {
        DockState state = DOCKS.get(view);
        if (state == null || state.probe != null || state.firstFrameChecked) return;
        ViewTreeObserver observer = view.getViewTreeObserver();
        if (!observer.isAlive()) return;
        WeakReference<View> reference = new WeakReference<>(view);
        ViewTreeObserver.OnPreDrawListener probe = new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                View target = reference.get();
                if (target == null) return true;
                DockState current = DOCKS.get(target);
                if (current == null) return true;
                if (target.getWidth() <= 0 || target.getHeight() <= 0) return true;
                if (!MiuixGlassHook.isBoundTo(target)
                        && !WorkstationRuntimeState.isActive()) {
                    ensureOutline(target, current);
                }
                if (current.startupFrames < 3) {
                    Outline shape = new Outline();
                    ViewOutlineProvider provider = target.getOutlineProvider();
                    if (provider != null) provider.getOutline(target, shape);
                    MainHook.log(TAG + " initial-frame=" + (++current.startupFrames)
                            + " material=" + target.getClass().getSimpleName()
                            + " size=" + target.getWidth() + "x" + target.getHeight()
                            + " radius=" + MiuixFolderGlassHook.readMaterialRadius(target)
                            + " drawable=" + drawableName(target)
                            + " clip=" + target.getClipToOutline()
                            + " canClip=" + shape.canClip()
                            + " shapeEmpty=" + shape.isEmpty()
                            + " outline=" + (provider == null ? "none"
                                    : provider.getClass().getSimpleName())
                            + " glassBound=" + MiuixGlassHook.isBoundTo(target));
                }
                if (current.startupFrames >= 3) {
                    current.firstFrameChecked = true;
                    current.removeProbe(target);
                }
                return true;
            }
        };
        state.probe = probe;
        observer.addOnPreDrawListener(probe);
    }

    private static String drawableName(View view) {
        Drawable background = view.getBackground();
        return background == null ? "none" : background.getClass().getSimpleName();
    }

    /**
     * Only the material View is clipped. The OEM controls the radius animation; the
     * provider recomputes from its current mCornerRadius on every outline invalidation.
     */
    private static void ensureOutline(View view, DockState state) {
        if (MiuixGlassHook.isBoundTo(view) || LauncherGlassStaticNode.find(view) != null) {
            releaseToPrismal(view);
            return;
        }
        if (WorkstationRuntimeState.isActive()) return;
        ViewOutlineProvider provider = view.getOutlineProvider();
        if (provider != state.ownedOutline) {
            // Workstation is excluded above. If OEM replaces its own outline on
            // radius commit, wrap the new provider rather than leaving it rectangular.
            state.originalOutline = provider;
            state.ownedOutline = new NativeOutline(
                    provider != null ? provider : ViewOutlineProvider.BACKGROUND, state);
            view.setOutlineProvider(state.ownedOutline);
        }
        if (!view.getClipToOutline()) view.setClipToOutline(true);
        view.invalidateOutline();
    }

    /** Release the exact material outline before the overscanned Prismal host is installed. */
    static void releaseToPrismal(View view) {
        if (view == null) return;
        DockState state = DOCKS.get(view);
        if (state == null) return;
        if (view.getOutlineProvider() == state.ownedOutline) {
            view.setOutlineProvider(state.originalOutline);
            if (view.getClipToOutline() && !state.originalClip) {
                view.setClipToOutline(false);
            }
        }
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
        final boolean originalClip;
        ViewOutlineProvider originalOutline;
        NativeOutline ownedOutline;
        boolean firstFrameChecked;
        int startupFrames;
        ViewTreeObserver.OnPreDrawListener probe;

        DockState(View view) {
            originalClip = view.getClipToOutline();
        }

        void removeProbe(View view) {
            if (probe == null) return;
            ViewTreeObserver tree = view.getViewTreeObserver();
            if (tree.isAlive()) tree.removeOnPreDrawListener(probe);
            probe = null;
        }
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
            // Native taskbar must be rectangular, even during mode transitions.
            if (dock != null && WorkstationRuntimeState.isActive()) {
                output.setRect(bounds);
                return;
            }
            float radius = MiuixFolderGlassHook.readMaterialRadius(view);
            if (!(radius > .5f)) radius = output.getRadius();
            if (!(radius > .5f) && dock != null) {
                radius = NativeDockCornerPolicy.provisionalRadius(
                        bounds.width(), bounds.height(), false);
            }
            if (!(radius > .5f) || !Float.isFinite(radius)) return;
            radius = Math.min(radius, .5f * Math.min(bounds.width(), bounds.height()));
            float alpha = output.getAlpha();
            if (!continuous) {
                // Preserve OEM round-rect geometry without a custom shader or any overlay.
                output.setRoundRect(bounds, radius);
                output.setAlpha(alpha);
                return;
            }
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
