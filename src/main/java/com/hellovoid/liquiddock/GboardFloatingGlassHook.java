package com.hellovoid.liquiddock;

import android.view.View;

/** Version-scoped hook for Gboard's popup floating-keyboard window only. */
final class GboardFloatingGlassHook {
    private static final String TAG = "[DC][GboardFloatingGlass]";
    private static final String POPUP_PROVIDER = "defpackage.pev";
    private static boolean installed;

    private GboardFloatingGlassHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (classLoader == null || runtimeConfig == null
                || !runtimeConfig.enabled || !runtimeConfig.glass.enabled) return false;
        try {
            HookUtil.hookMethod(classLoader, POPUP_PROVIDER, "b", chain -> {
                Object owner = chain.getThisObject();
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                View popup = popupView(owner);
                if (popup != null) {
                    GboardFloatingGlassCoordinator.onShown(popup, runtimeConfig.glass);
                }
                return result;
            });
            HookUtil.hookMethod(classLoader, POPUP_PROVIDER, "a", chain -> {
                Object owner = chain.getThisObject();
                View popup = popupView(owner);
                if (popup != null) {
                    GboardFloatingGlassCoordinator.onHidden(popup);
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            installed = true;
            log("hook installed provider=" + POPUP_PROVIDER, null);
            return true;
        } catch (Throwable error) {
            log("hook unavailable; stock floating background retained", error);
            return false;
        }
    }

    private static View popupView(Object owner) {
        if (owner == null) return null;
        try {
            Object value = HookUtil.getField(owner, "b");
            return value instanceof View ? (View) value : null;
        } catch (Throwable error) {
            log("popup root field unavailable", error);
            return null;
        }
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message, error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
