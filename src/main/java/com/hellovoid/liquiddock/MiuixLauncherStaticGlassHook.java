package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Adds ShortcutIcon and widget hosts to the one root-wide static Launcher glass compositor. */
final class MiuixLauncherStaticGlassHook {
    private static final String TAG = "[DC][StaticGlassHook]";
    private static final int MAX_BIND_ATTEMPTS = 8;
    private static final Map<View, View.OnAttachStateChangeListener> BOOTSTRAP_OBSERVERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;

    private MiuixLauncherStaticGlassHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (runtimeConfig == null || !runtimeConfig.enabled || !runtimeConfig.glass.enabled) {
            return false;
        }
        LiquidDockConfig.Glass glassConfig = runtimeConfig.glass;
        if (!glassConfig.widgetEnabled && !glassConfig.iconEnabled) return false;
        boolean any = false;
        if (glassConfig.iconEnabled) {
            any |= installHostClass(classLoader, "com.miui.home.launcher.ShortcutIcon",
                    LauncherGlassDragState.Kind.ICON, glassConfig);
        }
        if (glassConfig.widgetEnabled) {
            any |= installHostClass(classLoader, "com.miui.home.launcher.LauncherAppWidgetHostView",
                    LauncherGlassDragState.Kind.WIDGET, glassConfig);
            any |= installHostClass(classLoader, "com.miui.home.launcher.maml.MaMlHostView",
                    LauncherGlassDragState.Kind.WIDGET, glassConfig);
        }
        installed = any;
        if (any) MainHook.log(TAG + " widget/icon static glass hooks installed");
        return any;
    }

    private static boolean installHostClass(
            ClassLoader classLoader, String className,
            LauncherGlassDragState.Kind kind, LiquidDockConfig.Glass glassConfig) {
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            Constructor<?>[] constructors = type.getDeclaredConstructors();
            if (constructors.length == 0) return false;
            for (Constructor<?> constructor : constructors) {
                HookUtil.hook(constructor, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object result = chain.proceed(args);
                    Object owner = chain.getThisObject();
                    if (owner instanceof View) observeHost((View) owner, kind, glassConfig);
                    return result;
                });
            }
            MainHook.log(TAG + " hooked " + className + " constructors=" + constructors.length);
            return true;
        } catch (ClassNotFoundException missing) {
            MainHook.log(TAG + " optional host absent " + className);
            return false;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook failed " + className + ": " + error);
            return false;
        }
    }

    static void reconcileExistingHost(View host, LiquidDockConfig.Glass glassConfig) {
        if (host == null || glassConfig == null) return;
        String name = host.getClass().getName();
        if (glassConfig.iconStyle.enabled && (name.endsWith(".ShortcutIcon")
                || "ShortcutIcon".equals(host.getClass().getSimpleName()))) {
            observeHost(host, LauncherGlassDragState.Kind.ICON, glassConfig);
        } else if (glassConfig.widgetStyle.enabled
                && (name.endsWith(".LauncherAppWidgetHostView")
                || name.endsWith(".MaMlHostView"))) {
            LauncherGlassVendorMaterialSuppressor.claimWidget(host);
            observeHost(host, LauncherGlassDragState.Kind.WIDGET, glassConfig);
        }
    }

    private static void observeHost(
            View host, LauncherGlassDragState.Kind kind, LiquidDockConfig.Glass glassConfig) {
        if (host == null) return;
        synchronized (BOOTSTRAP_OBSERVERS) {
            if (BOOTSTRAP_OBSERVERS.containsKey(host)) return;
            View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                    scheduleBind(v, kind, glassConfig, 0);
                }
                @Override public void onViewDetachedFromWindow(View v) {}
            };
            BOOTSTRAP_OBSERVERS.put(host, listener);
            host.addOnAttachStateChangeListener(listener);
        }
        if (host.isAttachedToWindow()) scheduleBind(host, kind, glassConfig, 0);
    }

    private static void scheduleBind(
            View host, LauncherGlassDragState.Kind kind,
            LiquidDockConfig.Glass glassConfig, int attempt) {
        if (host == null || !host.isAttachedToWindow() || attempt > MAX_BIND_ATTEMPTS) return;
        if (host.getWidth() <= 0 || host.getHeight() <= 0) {
            host.postOnAnimation(() -> scheduleBind(host, kind, glassConfig, attempt + 1));
            return;
        }
        LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);
        if (node == null || node.kind() != kind) {
            float radius = resolveCornerRadius(host, kind);
            node = LauncherGlassStaticNode.attachToMaterial(host, kind, radius, glassConfig);
        } else {
            node.requestLifecycleRefresh();
        }
        if (node != null) {
            if (kind == LauncherGlassDragState.Kind.WIDGET)
                LauncherGlassVendorMaterialSuppressor.claimWidget(host);
            removeBootstrapObserver(host);
        }
    }

    private static void removeBootstrapObserver(View host) {
        View.OnAttachStateChangeListener listener;
        synchronized (BOOTSTRAP_OBSERVERS) {
            listener = BOOTSTRAP_OBSERVERS.remove(host);
        }
        if (listener != null) host.removeOnAttachStateChangeListener(listener);
    }

    private static float resolveCornerRadius(View host, LauncherGlassDragState.Kind kind) {
        if (kind == LauncherGlassDragState.Kind.ICON) {
            LauncherGlassIconGeometry.Bounds bounds = LauncherGlassIconGeometry.resolve(host);
            float min = bounds != null
                    ? Math.min(bounds.width(), bounds.height())
                    : Math.min(Math.max(1, host.getWidth()), Math.max(1, host.getHeight()));
            android.graphics.drawable.Drawable drawable = null;
            if (host instanceof android.widget.TextView) {
                android.graphics.drawable.Drawable[] drawables =
                        ((android.widget.TextView) host).getCompoundDrawables();
                if (drawables.length > 1) drawable = drawables[1];
            }
            return LauncherGlassIconShapeResolver.resolveAutoRadius(
                    drawable, min, min, min * 0.22f);
        }
        float nativeRadius = readCornerRadius(host);
        if (Float.isFinite(nativeRadius) && nativeRadius > 0f) return nativeRadius;
        float min = Math.min(Math.max(1, host.getWidth()), Math.max(1, host.getHeight()));
        return Math.max(0f, min * 0.08f);
    }

    private static float readCornerRadius(View host) {
        if (host == null) return Float.NaN;
        try {
            Field field = findField(host.getClass(), "mCornerRadius");
            field.setAccessible(true);
            Object value = field.get(host);
            if (value instanceof Number) return Math.max(0f, ((Number) value).floatValue());
        } catch (Throwable ignored) {}
        Drawable background = host.getBackground();
        if (background instanceof GradientDrawable) {
            return Math.max(0f, ((GradientDrawable) background).getCornerRadius());
        }
        return Float.NaN;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { current = current.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
}
