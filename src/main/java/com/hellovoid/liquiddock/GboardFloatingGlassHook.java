package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Field;

/** Version-scoped hook for Gboard's popup floating-keyboard window only. */
final class GboardFloatingGlassHook {
    private static final String TAG = "[DC][GboardFloatingGlass]";
    private static boolean installed;

    private GboardFloatingGlassHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (classLoader == null || runtimeConfig == null
                || !runtimeConfig.enabled || !runtimeConfig.glass.enabled) return false;
        try {
            GboardFloatingTargetResolver.Target target =
                    GboardFloatingTargetResolver.resolve(classLoader);
            HookUtil.hook(target.showMethod, chain -> {
                Object owner = chain.getThisObject();
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                View popup = popupView(owner, target.popupViewField);
                if (popup != null) {
                    GboardFloatingGlassCoordinator.onShown(popup, runtimeConfig.glass);
                }
                return result;
            });
            HookUtil.hook(target.hideMethod, chain -> {
                Object owner = chain.getThisObject();
                View popup = popupView(owner, target.popupViewField);
                if (popup != null) {
                    GboardFloatingGlassCoordinator.onHidden(popup);
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            installed = true;
            log("hook installed provider=" + target.binaryName
                    + " runtimeClass=" + target.providerClass.getName()
                    + " popupField=" + target.popupViewField.getName(), null);
            return true;
        } catch (Throwable error) {
            log("hook unavailable cause=" + failureSummary(error)
                    + "; stock floating background retained", error);
            return false;
        }
    }

    private static View popupView(Object owner, Field popupViewField) {
        if (owner == null || popupViewField == null) return null;
        try {
            Object value = popupViewField.get(owner);
            return value instanceof View ? (View) value : null;
        } catch (Throwable error) {
            log("popup root field unavailable cause=" + failureSummary(error), error);
            return null;
        }
    }

    private static String failureSummary(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        String summary = error.getClass().getName()
                + (message == null || message.isEmpty() ? "" : ": " + message);
        Throwable cause = error.getCause();
        if (cause != null && cause != error) {
            String causeMessage = cause.getMessage();
            summary += " <- " + cause.getClass().getName()
                    + (causeMessage == null || causeMessage.isEmpty()
                    ? "" : ": " + causeMessage);
        }
        return summary;
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message, error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
