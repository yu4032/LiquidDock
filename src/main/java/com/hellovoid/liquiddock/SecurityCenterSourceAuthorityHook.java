package com.hellovoid.liquiddock;

import android.content.ComponentName;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/** Hooks the native Security Center activity observer as zero-copy source-authority boundary. */
final class SecurityCenterSourceAuthorityHook {
    private static final String TAG = "[DC][SecurityCenterGlass]";
    private static final Set<Method> INSTALLED_CALLBACKS = new HashSet<>();
    private static boolean bootstrapInstalled;

    private SecurityCenterSourceAuthorityHook() {}

    static synchronized boolean install(ClassLoader classLoader) {
        if (bootstrapInstalled) return true;
        if (classLoader == null) return false;
        try {
            Class<?> serviceClass = Class.forName(
                    SecurityCenterHookSpec.BOOTSTRAP_SERVICE_CLASS, false, classLoader);
            SecurityCenterSourceAuthorityContractResolver.Contract contract =
                    SecurityCenterSourceAuthorityContractResolver.resolve(
                            serviceClass, ComponentName.class);
            Method onCreate = HookUtil.findMethodExact(serviceClass, "onCreate", new Class<?>[0]);
            HookUtil.hook(onCreate, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                installLiveCallback(contract, chain.getThisObject());
                return result;
            });
            bootstrapInstalled = true;
            log("activity source-authority bootstrap installed listener="
                    + contract.listenerClass().getName(), null);
            return true;
        } catch (Throwable error) {
            log("activity source-authority bootstrap unavailable; custom glass stays disabled", error);
            return false;
        }
    }

    private static synchronized void installLiveCallback(
            SecurityCenterSourceAuthorityContractResolver.Contract contract,
            Object service) {
        try {
            Method callback = contract.resolveConcreteCallback(service);
            if (!INSTALLED_CALLBACKS.contains(callback)) {
                HookUtil.hook(callback, chain -> {
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
                INSTALLED_CALLBACKS.add(callback);
                log("live activity source-authority callback installed class="
                        + callback.getDeclaringClass().getName(), null);
            }
            SecurityCenterGlassRuntimeState.onSourceAuthorityAvailable();
        } catch (Throwable error) {
            log("live activity source-authority callback unavailable; custom glass stays disabled", error);
        }
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message + ": " + error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
