package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.ref.WeakReference;

/** Owns Launcher HotSeats native-shadow state and reversible vendor shadow overrides. */
final class DockShadowOwnership {
    private static WeakReference<View> activeBackgroundRef = new WeakReference<>(null);
    private static WeakReference<Object> hotSeatsOwnerRef = new WeakReference<>(null);
    private static volatile LiquidDockConfig.Dock nativeShadowConfig;

    private DockShadowOwnership() {}

    static void install(ClassLoader classLoader, LiquidDockConfig.Dock dock) {
        nativeShadowConfig = dock;
        installNativeDockShadowOwnership(classLoader);
        installDockShadowSetupHook(classLoader);
    }

    static View activeBackground() {
        return activeBackgroundRef.get();
    }

    static void rememberBackground(View view) {
        activeBackgroundRef = new WeakReference<>(view);
    }

    static void syncDockShadow(View dockBg, LiquidDockConfig.Dock dock) {
        if (dockBg != null) rememberBackground(dockBg);
        if (dock != null) nativeShadowConfig = dock;
    }

    static void refreshVendorDockShadow() {
        Object hotSeats = hotSeatsOwnerRef.get();
        if (hotSeats == null) return;
        HookUtil.InvocationResult<Object> refresh = HookUtil.tryInvoke(hotSeats, "showViewShadow");
        if (!refresh.succeeded()) {
            MainHook.log("[DC] HotSeats native shadow refresh failed: " + refresh.failure());
        }
    }

    static void onRuntimeDockShadowDisabled() {
        if (!DockShadowRuntimePolicy.shouldRefreshVendorShadow(
                WorkstationRuntimeState.isActive(),
                VisualRuntimeState.isDockCustomizationEnabled())) {
            return;
        }
        refreshVendorDockShadow();
    }

    static void onRuntimeDockShadowEnabled() {
        if (!DockShadowRuntimePolicy.shouldRefreshVendorShadow(
                WorkstationRuntimeState.isActive(),
                VisualRuntimeState.isDockCustomizationEnabled())) {
            return;
        }
        try {
            nativeShadowConfig = LiquidDockConfig.load().dock;
        } catch (Throwable ignored) {}
        refreshVendorDockShadow();
    }

    static void onRuntimeDockCustomizationDisabled() {
        refreshVendorDockShadow();
        DockStrokeRenderer.refreshInstalledFromCurrentConfig();
        View dockBg = activeBackground();
        if (dockBg != null) dockBg.postInvalidateOnAnimation();
    }

    private static void setHotSeatsShadowOwner(Object hotSeats) {
        if (hotSeats != null) hotSeatsOwnerRef = new WeakReference<>(hotSeats);
    }

    private static void installNativeDockShadowOwnership(ClassLoader classLoader) {
        try {
            Class<?> hotSeatsClass = Class.forName(
                    "com.miui.home.launcher.hotseats.HotSeats", false, classLoader);
            java.lang.reflect.Method showViewShadow =
                    hotSeatsClass.getDeclaredMethod("showViewShadow");
            showViewShadow.setAccessible(true);
            HookUtil.hook(showViewShadow, chain -> {
                Object hotSeats = chain.getThisObject();
                setHotSeatsShadowOwner(hotSeats);
                HotSeatsShadowScope scope = pushConfiguredHotSeatsShadow(hotSeats);
                try {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                } finally {
                    scope.close();
                }
            });

            try {
                java.lang.reflect.Method setTranslationY =
                        hotSeatsClass.getDeclaredMethod("setTranslationY", float.class);
                setTranslationY.setAccessible(true);
                HookUtil.hook(setTranslationY, chain -> {
                    setHotSeatsShadowOwner(chain.getThisObject());
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                });
            } catch (Throwable error) {
                MainHook.log("[DC] HotSeats setTranslationY shadow hook unavailable: " + error);
            }
            MainHook.log("[DC] HotSeats native Dock shadow lifecycle hooked");
        } catch (Throwable error) {
            MainHook.log("[DC] native Dock shadow lifecycle unavailable: " + error);
        }
    }

    private static HotSeatsShadowScope pushConfiguredHotSeatsShadow(Object hotSeats) {
        LiquidDockConfig.Dock dock = currentNativeShadowConfig();
        if (hotSeats == null || !DockShadowRuntimePolicy.shouldApplyTemporaryOverrides(
                WorkstationRuntimeState.isActive(),
                VisualRuntimeState.isDockCustomizationEnabled(),
                dock != null)) {
            return HotSeatsShadowScope.noop();
        }

        float density = hotSeats instanceof View
                ? ((View) hotSeats).getResources().getDisplayMetrics().density
                : android.content.res.Resources.getSystem().getDisplayMetrics().density;
        float scale = dock.dimensionsDp ? density : 1f;
        float radiusPx = Math.min(
                Math.max(0f, dock.shadowRadius * scale),
                Math.max(0f, dock.shadowSize * scale));
        float offsetYPx = dock.shadowY * scale;

        HotSeatsShadowScope scope = new HotSeatsShadowScope(hotSeats);
        scope.overrideNumber("mMiShadowRadius", radiusPx);
        scope.overrideNumber("mMiShadowOffsetY", offsetYPx);
        return scope;
    }

    private static LiquidDockConfig.Dock currentNativeShadowConfig() {
        LiquidDockConfig.Dock current = nativeShadowConfig;
        if (current != null) return current;
        try {
            current = LiquidDockConfig.load().dock;
            nativeShadowConfig = current;
        } catch (Throwable ignored) {}
        return current;
    }

    private static void installDockShadowSetupHook(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.Launcher", "setupViews",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        try {
                            nativeShadowConfig = LiquidDockConfig.load().dock;
                            Object hotSeats = HookUtil.getField(chain.getThisObject(), "mHotSeats");
                            if (hotSeats == null) return result;
                            setHotSeatsShadowOwner(hotSeats);
                            View background = resolveActiveDockBackground(hotSeats);
                            if (background != null) rememberBackground(background);
                            refreshVendorDockShadow();
                        } catch (Throwable error) {
                            MainHook.log("[DC] Dock shadow setup failed: " + error);
                        }
                        return result;
                    });
        } catch (Throwable error) {
            MainHook.log("[DC] Dock shadow setup hook unavailable: " + error);
        }
    }

    private static View resolveActiveDockBackground(Object hotSeats) {
        if (hotSeats == null) return null;
        HookUtil.InvocationResult<Object> active =
                HookUtil.tryInvoke(hotSeats, "getHotSeatsBackground");
        if (active.succeeded() && active.value() instanceof View) {
            return (View) active.value();
        }
        try {
            Object compat = HookUtil.getField(hotSeats, "mBlurBackground2");
            return compat instanceof View ? (View) compat : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setNumericField(
            java.lang.reflect.Field field, Object target, float value) throws IllegalAccessException {
        Class<?> type = field.getType();
        if (type == float.class || type == Float.class) field.set(target, value);
        else if (type == double.class || type == Double.class) field.set(target, (double) value);
        else if (type == int.class || type == Integer.class) field.set(target, Math.round(value));
        else if (type == long.class || type == Long.class) field.set(target, (long) Math.round(value));
        else if (type == short.class || type == Short.class) field.set(target, (short) Math.round(value));
        else if (type == byte.class || type == Byte.class) field.set(target, (byte) Math.round(value));
        else throw new IllegalArgumentException("not numeric: " + type);
    }

    private static final class HotSeatsShadowScope implements AutoCloseable {
        private static final HotSeatsShadowScope NOOP = new HotSeatsShadowScope(null);
        private final Object target;
        private final java.util.ArrayList<ShadowFieldState> fields = new java.util.ArrayList<>();

        HotSeatsShadowScope(Object target) {
            this.target = target;
        }

        static HotSeatsShadowScope noop() {
            return NOOP;
        }

        boolean overrideNumber(String name, float value) {
            if (target == null) return false;
            try {
                java.lang.reflect.Field field = HookUtil.findField(target.getClass(), name);
                Object oldValue = field.get(target);
                setNumericField(field, target, value);
                fields.add(new ShadowFieldState(field, oldValue));
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        @Override
        public void close() {
            if (target == null) return;
            for (int i = fields.size() - 1; i >= 0; i--) {
                ShadowFieldState state = fields.get(i);
                try {
                    state.field.set(target, state.value);
                } catch (Throwable ignored) {}
            }
            fields.clear();
        }
    }

    private static final class ShadowFieldState {
        final java.lang.reflect.Field field;
        final Object value;

        ShadowFieldState(java.lang.reflect.Field field, Object value) {
            this.field = field;
            this.value = value;
        }
    }
}
