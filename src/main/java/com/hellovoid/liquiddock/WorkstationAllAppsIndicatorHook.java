package com.hellovoid.liquiddock;

import android.content.res.Configuration;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * Adjusts only the native Workstation All Apps page indicator.
 * The vendor retains layout and animation authority; our translation is always relative
 * to the position produced by updateIndicatorPositions, and restored before each update.
 */
final class WorkstationAllAppsIndicatorHook {
    private static final WeakHashMap<View, Float> BASE_Y = new WeakHashMap<>();
    private static final WeakHashMap<View, PositionGuard> GUARDS = new WeakHashMap<>();
    private static HomeGridWorkstationGeometryConfig config =
            HomeGridWorkstationGeometryConfig.NONE;

    private WorkstationAllAppsIndicatorHook() {}

    static void install(ClassLoader loader, HomeGridWorkstationGeometryConfig settings) {
        config = settings == null ? HomeGridWorkstationGeometryConfig.NONE : settings;
        try {
            Class<?> screenView = Class.forName(
                    "com.miui.home.launcher.ScreenView", false, loader);
            Class<?> workspace = Class.forName(
                    "com.miui.home.launcher.Workspace", false, loader);
            HookUtil.hookMethod(screenView, "updateIndicatorPositions",
                    new Class<?>[]{int.class, boolean.class}, chain -> {
                        Object owner = chain.getThisObject();
                        View indicator = screenIndicator(owner);
                        if (indicator != null) restoreVendorY(indicator);
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        // Exclude HOME explicitly: some vendor Workspaces share ScreenView code.
                        if (workspace.isInstance(owner) || !isAllAppsScreen(owner)) {
                            return result;
                        }
                        indicator = screenIndicator(owner);
                        if (indicator != null) captureVendorY(indicator);
                        return result;
                    });
            MainHook.log("[DC][WorkstationAllAppsIndicator] installed");
        } catch (Throwable error) {
            MainHook.log("[DC][WorkstationAllAppsIndicator] unavailable: " + error);
        }
    }

    private static View screenIndicator(Object owner) {
        HookUtil.InvocationResult<Object> value =
                HookUtil.tryInvoke(owner, "getScreenIndicator");
        return value.succeeded() && value.value() instanceof View
                ? (View) value.value() : null;
    }

    private static boolean isAllAppsScreen(Object owner) {
        if (!MainHook.isWorkstationMode() || !(owner instanceof View)) return false;
        HookUtil.InvocationResult<Object> exact =
                HookUtil.tryInvoke(owner, "isInLapTopAllApps");
        if (exact.succeeded() && Boolean.TRUE.equals(exact.value())) return true;

        View cursor = (View) owner;
        for (int depth = 0; cursor != null && depth < 12; depth++) {
            if (WorkstationLayoutClassifier.matches(
                    false, "", cursor.getClass().getName())) {
                return true;
            }
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return false;
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
