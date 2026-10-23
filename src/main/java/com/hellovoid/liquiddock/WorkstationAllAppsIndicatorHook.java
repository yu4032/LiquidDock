package com.hellovoid.liquiddock;

import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

/**
 * Applies independent portrait/landscape offsets to the workstation All Apps indicator.
 *
 * Launcher 4.50 places ScreenView$SeekBarIndicator directly under AllAppsWorkspace.
 * Its getter is not exposed, so the view is resolved from that workspace only.
 * OEM layout remains authoritative: restore its translation before each update,
 * then apply our offset relative to the resulting vendor position.
 */
final class WorkstationAllAppsIndicatorHook {
    private static final String ALL_APPS_WORKSPACE =
            "com.miui.home.launcher.laptop.launchpad.AllAppsWorkspace";

    private static final WeakHashMap<View, Float> VENDOR_Y = new WeakHashMap<>();
    private static final WeakHashMap<View, PositionGuard> GUARDS = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> UPDATING =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static volatile Class<?> workspaceClass;
    private static volatile HomeGridWorkstationGeometryConfig config =
            HomeGridWorkstationGeometryConfig.NONE;

    private WorkstationAllAppsIndicatorHook() {}

    static boolean isTargetWorkspace(Object owner) {
        if (owner == null) return false;
        Class<?> target = workspaceClass;
        return target != null ? target.isInstance(owner)
                : ALL_APPS_WORKSPACE.equals(owner.getClass().getName());
    }

    static void install(ClassLoader loader, HomeGridWorkstationGeometryConfig settings) {
        config = settings == null ? HomeGridWorkstationGeometryConfig.NONE : settings;
        final Class<?> allApps;
        try {
            allApps = Class.forName(ALL_APPS_WORKSPACE, false, loader);
            workspaceClass = allApps;
        } catch (Throwable error) {
            MainHook.log("[DC][WorkstationAllAppsIndicator] workspace unavailable: " + error);
            return;
        }

        // Resolve independently: a missing OEM method must not disable other layout paths.
        hookBoundary(allApps, "updateIndicatorPositions",
                new Class<?>[]{int.class, boolean.class});
        hookBoundary(allApps, "onLayout",
                new Class<?>[]{boolean.class, int.class, int.class, int.class, int.class});
        hookBoundary(allApps, "onAttachedToWindow", new Class<?>[0]);
    }

    private static void hookBoundary(Class<?> target, String method, Class<?>[] parameters) {
        try {
            Method boundary = HookUtil.findMethodExact(target, method, parameters);
            // Never hook framework-wide View attachment callbacks.
            if ("onAttachedToWindow".equals(method)
                    && !boundary.getDeclaringClass().getName().startsWith("com.miui.home.")) {
                return;
            }
            HookUtil.hook(boundary, WorkstationAllAppsIndicatorHook::aroundVendorUpdate);
        } catch (Throwable error) {
            MainHook.log("[DC][WorkstationAllAppsIndicator] "
                    + method + " unavailable: " + error);
        }
    }

    private static Object aroundVendorUpdate(XposedInterface.Chain chain) throws Throwable {
        Object owner = chain.getThisObject();
        Object[] args = chain.getArgs().toArray(new Object[0]);
        if (!isTargetWorkspace(owner) || Boolean.TRUE.equals(UPDATING.get())) {
            return chain.proceed(args);
        }

        UPDATING.set(Boolean.TRUE);
        try {
            View before = indicatorOf(owner);
            if (before != null) restoreVendorY(before);
            Object result = chain.proceed(args);
            View after = indicatorOf(owner);
            if (after != null) captureAndApply(after);
            return result;
        } finally {
            UPDATING.remove();
        }
    }

    private static View indicatorOf(Object owner) {
        if (!(owner instanceof ViewGroup)) return null;
        ViewGroup workspace = (ViewGroup) owner;

        // The first path matches the verified Launcher 4.50 hierarchy.
        for (int i = 0; i < workspace.getChildCount(); i++) {
            View child = workspace.getChildAt(i);
            if (child != null && WorkstationAllAppsIndicatorPolicy.isIndicatorClass(
                    child.getClass().getName())) {
                return child;
            }
        }

        // Compatibility for OEM builds that wrap the same indicator once.
        for (int i = 0; i < workspace.getChildCount(); i++) {
            View child = workspace.getChildAt(i);
            if (!(child instanceof ViewGroup)) continue;
            ViewGroup group = (ViewGroup) child;
            for (int j = 0; j < group.getChildCount(); j++) {
                View nested = group.getChildAt(j);
                if (nested != null && WorkstationAllAppsIndicatorPolicy.isIndicatorClass(
                        nested.getClass().getName())) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static void restoreVendorY(View indicator) {
        Float baseline;
        synchronized (VENDOR_Y) {
            baseline = VENDOR_Y.get(indicator);
        }
        if (baseline != null && indicator.getTranslationY() != baseline) {
            indicator.setTranslationY(baseline);
        }
    }

    private static void captureAndApply(View indicator) {
        float baseline = indicator.getTranslationY();
        synchronized (VENDOR_Y) {
            VENDOR_Y.put(indicator, baseline);
        }
        synchronized (GUARDS) {
            if (!GUARDS.containsKey(indicator)) {
                PositionGuard guard = new PositionGuard(indicator);
                GUARDS.put(indicator, guard);
                indicator.getViewTreeObserver().addOnPreDrawListener(guard);
                indicator.addOnAttachStateChangeListener(guard);
            }
        }
        apply(indicator, baseline);
    }

    private static void apply(View indicator, float baseline) {
        boolean portrait = indicator.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
        HomeGridWorkstationGeometryConfig current = config;
        float target = WorkstationAllAppsIndicatorPolicy.targetY(
                baseline, MainHook.isWorkstationMode(), true, portrait,
                current.allAppsLandscapeIndicatorY, current.allAppsPortraitIndicatorY);
        if (indicator.getTranslationY() != target) indicator.setTranslationY(target);
    }

    private static final class PositionGuard implements
            ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        private final WeakReference<View> indicatorRef;

        PositionGuard(View indicator) {
            indicatorRef = new WeakReference<>(indicator);
        }

        @Override
        public boolean onPreDraw() {
            View indicator = indicatorRef.get();
            if (indicator == null) return true;
            Float baseline;
            synchronized (VENDOR_Y) {
                baseline = VENDOR_Y.get(indicator);
            }
            if (baseline != null) apply(indicator, baseline);
            return true;
        }

        @Override
        public void onViewAttachedToWindow(View view) {}

        @Override
        public void onViewDetachedFromWindow(View view) {
            ViewTreeObserver observer = view.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnPreDrawListener(this);
            view.removeOnAttachStateChangeListener(this);
            synchronized (GUARDS) {
                if (GUARDS.get(view) == this) GUARDS.remove(view);
            }
            synchronized (VENDOR_Y) {
                VENDOR_Y.remove(view);
            }
            indicatorRef.clear();
        }
    }
}
