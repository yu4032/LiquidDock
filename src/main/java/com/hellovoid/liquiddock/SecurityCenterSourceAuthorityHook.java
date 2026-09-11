package com.hellovoid.liquiddock;

import android.content.ComponentName;

/** Hooks the native Security Center activity observer as zero-copy source-authority boundary. */
final class SecurityCenterSourceAuthorityHook {
    private static final String TAG = "[DC][SecurityCenterGlass]";
    private static boolean installed;

    private SecurityCenterSourceAuthorityHook() {}

    static synchronized boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> serviceClass = Class.forName(
                    SecurityCenterHookSpec.BOOTSTRAP_SERVICE_CLASS, false, classLoader);
            SecurityCenterSourceAuthorityContractResolver.Contract contract =
                    SecurityCenterSourceAuthorityContractResolver.resolve(
                            serviceClass, ComponentName.class);
            HookUtil.hook(contract.onActivityChanged(), chain -> {
                Object previous = chain.getArgs().size() > 0 ? chain.getArgs().get(0) : null;
                Object current = chain.getArgs().size() > 1 ? chain.getArgs().get(1) : null;
                if (previous instanceof ComponentName && current instanceof ComponentName
                        && !previous.equals(current)
                        && SecurityCenterGlassRuntimeState.isEnabled()) {
                    try {
                        SecurityCenterGlassRuntimeState.onSourceAuthorityChanged(previous, current);
                    } catch (Throwable error) {
                        log("activity source-authority callback failed closed", error);
                    }
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            installed = true;
            log("activity source-authority hook installed listener="
                    + contract.listenerClass().getName(), null);
            return true;
        } catch (Throwable error) {
            log("activity source-authority hook unavailable; custom glass stays disabled", error);
            return false;
        }
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message + ": " + error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
