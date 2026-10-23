package com.hellovoid.liquiddock;

import android.content.res.Configuration;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Owns Workspace page-indicator translation capture, application and restoration. */
final class HomeGridPageIndicatorHook {
    private static final WeakHashMap<View, float[]> BASE_TRANSLATIONS = new WeakHashMap<>();
    private static final WeakHashMap<View, IndicatorPositionGuard> POSITION_GUARDS =
            new WeakHashMap<>();

    private static HomeGridInstallConfig config;

    private HomeGridPageIndicatorHook() {}

    static void install(ClassLoader classLoader, HomeGridInstallConfig installConfig) {
        config = installConfig;
        Class<?> screenView;
        Class<?> workspace;
        try {
            screenView = Class.forName("com.miui.home.launcher.ScreenView", false, classLoader);
            workspace = Class.forName("com.miui.home.launcher.Workspace", false, classLoader);
        } catch (ClassNotFoundException error) {
            throw new RuntimeException(error);
        }

        final Class<?> workspaceClass = workspace;
        HookUtil.hookMethod(screenView, "updateIndicatorPositions",
                new Class<?>[]{int.class, boolean.class}, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object owner = chain.getThisObject();
                    if (workspaceClass.isInstance(owner)) {
                        indicator(owner, true);
                    }
                    Object result = chain.proceed(args);
                    if (workspaceClass.isInstance(owner)) {
                        indicator(owner, false);
                    }
                    return result;
                });
    }

    private static void indicator(Object workspace, boolean restore) {
        HookUtil.InvocationResult<Object> result =
                HookUtil.tryInvoke(workspace, "getScreenIndicator");
        if (!result.succeeded() || !(result.value() instanceof View)) return;
        View indicator = (View) result.value();
        if (restore) restoreIndicatorTranslation(indicator);
        else captureAndApplyIndicatorTranslation(indicator);
    }

    private static void restoreIndicatorTranslation(View indicator) {
        float[] base;
        synchronized (BASE_TRANSLATIONS) {
            base = BASE_TRANSLATIONS.get(indicator);
        }
        if (base == null) return;
        indicator.setTranslationX(base[0]);
        indicator.setTranslationY(base[1]);
    }

    private static void captureAndApplyIndicatorTranslation(View indicator) {
        float[] base = new float[]{indicator.getTranslationX(), indicator.getTranslationY()};
        synchronized (BASE_TRANSLATIONS) {
            BASE_TRANSLATIONS.put(indicator, base);
        }
        ensureIndicatorPositionGuard(indicator);
        applyIndicatorTranslation(indicator, base);
    }

    private static void ensureIndicatorPositionGuard(View indicator) {
        synchronized (POSITION_GUARDS) {
            if (POSITION_GUARDS.containsKey(indicator)) return;
            IndicatorPositionGuard guard = new IndicatorPositionGuard(indicator);
            POSITION_GUARDS.put(indicator, guard);
            indicator.getViewTreeObserver().addOnPreDrawListener(guard);
            indicator.addOnAttachStateChangeListener(guard);
        }
    }

    private static void applyIndicatorTranslation(View indicator, float[] base) {
        HomeGridInstallConfig current = config;
        if (current == null) return;
        if (MainHook.isWorkstationMode()) {
            indicator.setTranslationX(base[0]);
            indicator.setTranslationY(base[1]);
            return;
        }
        boolean portrait = indicator.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
        float targetY = base[1] + current.orientation(portrait).indicatorY;
        indicator.setTranslationX(base[0]);
        if (indicator.getTranslationY() != targetY) indicator.setTranslationY(targetY);
    }

    private static final class IndicatorPositionGuard implements
            ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        private final WeakReference<View> indicatorRef;

        IndicatorPositionGuard(View indicator) {
            indicatorRef = new WeakReference<>(indicator);
        }

        @Override
        public boolean onPreDraw() {
            View indicator = indicatorRef.get();
            if (indicator == null) return true;
            float[] base;
            synchronized (BASE_TRANSLATIONS) {
                base = BASE_TRANSLATIONS.get(indicator);
            }
            if (base != null) applyIndicatorTranslation(indicator, base);
            return true;
        }

        @Override
        public void onViewAttachedToWindow(View view) {}

        @Override
        public void onViewDetachedFromWindow(View view) {
            try {
                ViewTreeObserver observer = view.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnPreDrawListener(this);
            } catch (Throwable ignored) {}
            try {
                view.removeOnAttachStateChangeListener(this);
            } catch (Throwable ignored) {}
            synchronized (POSITION_GUARDS) {
                if (POSITION_GUARDS.get(view) == this) POSITION_GUARDS.remove(view);
            }
            synchronized (BASE_TRANSLATIONS) {
                BASE_TRANSLATIONS.remove(view);
            }
            indicatorRef.clear();
        }
    }
}
