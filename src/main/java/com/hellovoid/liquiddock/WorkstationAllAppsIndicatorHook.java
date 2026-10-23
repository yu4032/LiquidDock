package com.hellovoid.liquiddock;

import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Positions only the Workstation launchpad AllAppsWorkspace indicator.
 *
 * AllAppsWorkspace can inherit Launcher Workspace/ScreenView: the generic HOME
 * indicator hook must never control it. This hook binds directly to the vendor
 * launchpad class and captures its actual indicator translation after layout,
 * without modifying either cell geometry or any system-wide View property.
 */
final class WorkstationAllAppsIndicatorHook {
    private static final String ALL_APPS_WORKSPACE =
            "com.miui.home.launcher.laptop.launchpad.AllAppsWorkspace";
    private static final WeakHashMap<View, Float> BASE_Y = new WeakHashMap<>();
    private static final WeakHashMap<View, PositionGuard> GUARDS = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> UPDATING =
            ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static volatile HomeGridWorkstationGeometryConfig config =
            HomeGridWorkstationGeometryConfig.NONE;
    private static volatile Class<?> targetClass;

    private WorkstationAllAppsIndicatorHook() {}

    static boolean isTargetWorkspace(Object owner) {
        if (owner == null) return false;
        Class<?> type = targetClass;
        return type != null ? type.isInstance(owner)
                : ALL_APPS_WORKSPACE.equals(owner.getClass().getName());
    }

    static void install(ClassLoader loader, HomeGridWorkstationGeometryConfig settings) {
        config = settings == null ? HomeGridWorkstationGeometryConfig.NONE : settings;
        try {
            Class<?> allApps = Class.forName(ALL_APPS_WORKSPACE, false, loader);
            targetClass = allApps;

            // Hook the declaring vendor method (possibly inherited from ScreenView)
            // but act *only* when the receiver is the dedicated All Apps workspace.
            Method positions = HookUtil.findMethodExact(allApps,
                    "updateIndicatorPositions", new Class<?>[]{int.class, boolean.class});
            HookUtil.hook(positions, WorkstationAllAppsIndicatorHook::aroundVendorLayout);
            MainHook.log("[DC][WorkstationAllAppsIndicator] positions="
                    + positions.getDeclaringClass().getName());

            // The workstation panel may lay out after the position callback, or
            // not invoke the position callback on rotation. Observe that boundary
            // as well, using the same reentrancy guard to avoid cumulative offsets.
            try {
                Method layout = HookUtil.findMethodExact(allApps, "onLayout",
                        new Class<?>[]{boolean.class, int.class, int.class,
                                int.class, int.class});
                HookUtil.hook(layout, WorkstationAllAppsIndicatorHook::aroundVendorLayout);
                MainHook.log("[DC][WorkstationAllAppsIndicator] layout="
                        + layout.getDeclaringClass().getName());
            } catch (Throwable error) {
                MainHook.log("[DC][WorkstationAllAppsIndicator] optional layout unavailable: "
                        + error);
            }
        } catch (Throwable error) {
            MainHook.log("[DC][WorkstationAllAppsIndicator] unavailable: " + error);
        }
    }

    private static Object aroundVendorLayout(io.github.libxposed.api.XposedInterface.Chain chain)
            throws Throwable {
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
            if (after != null) captureVendorY(after);
            return result;
        } finally {
            UPDATING.remove();
        }
    }

    private static View indicatorOf(Object owner) {
        HookUtil.InvocationResult<Object> result =
                HookUtil.tryInvoke(owner, "getScreenIndicator");
        if (result.succeeded() && result.value() instanceof View) {
            return (View) result.value();
        }
        // The launchpad may host the indicator as a direct descendant rather
        // than expose the ScreenView getter. Do not search beyond this workspace.
        return owner instanceof ViewGroup ? findIndicator((ViewGroup) owner, 0) : null;
    }

    private static View findIndicator(ViewGroup parent, int depth) {
        if (depth > 8) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == null) continue;
            String name = child.getClass().getName();
            if (name.contains("ScreenIndicator") || name.contains("PageIndicator")) {
                return child;
            }
            if (child instanceof ViewGroup) {
                View result = findIndicator((ViewGroup) child, depth + 1);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static void restoreVendorY(View indicator) {
        Float base;
        synchronized (BASE_Y) {
            base = BASE_Y.get(indicator);
        }
        if (base != null && indicator.getTranslationY() != base) {
            indicator.setTranslationY(base);
        }
    }

    private static void captureVendorY(View indicator) {
        float vendorY = indicator.getTranslationY();
        synchronized (BASE_Y) {
            BASE_Y.put(indicator, vendorY);
        }
        synchronized (GUARDS) {
            if (!GUARDS.containsKey(indicator)) {
                PositionGuard guard = new PositionGuard(indicator);
                GUARDS.put(indicator, guard);
                indicator.getViewTreeObserver().addOnPreDrawListener(guard);
                indicator.addOnAttachStateChangeListener(guard);
                MainHook.log("[DC][WorkstationAllAppsIndicator] bound "
                        + indicator.getClass().getName());
            }
        }
        apply(indicator, vendorY);
    }

    private static void apply(View indicator, float vendorY) {
        boolean portrait = indicator.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
        HomeGridWorkstationGeometryConfig current = config;
        float y = WorkstationAllAppsIndicatorPolicy.targetY(
                vendorY, MainHook.isWorkstationMode(), true, portrait,
                current.allAppsLandscapeIndicatorY, current.allAppsPortraitIndicatorY);
        if (indicator.getTranslationY() != y) indicator.setTranslationY(y);
    }

    private static final class PositionGuard implements
            ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        private final WeakReference<View> indicator;

        PositionGuard(View view) {
            indicator = new WeakReference<>(view);
        }

        @Override
        public boolean onPreDraw() {
            View view = indicator.get();
            if (view == null) return true;
            Float baseline;
            synchronized (BASE_Y) {
                baseline = BASE_Y.get(view);
            }
            if (baseline != null) apply(view, baseline);
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
            synchronized (BASE_Y) {
                BASE_Y.remove(view);
            }
            indicator.clear();
        }
    }
}
