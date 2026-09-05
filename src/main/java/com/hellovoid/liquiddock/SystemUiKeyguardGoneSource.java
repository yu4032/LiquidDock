package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.Intent;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Read-only SystemUI observer for the exact keyguard transition that authorizes HOME wallpaper
 * capture. It never inspects or mutates SurfaceControl state and never performs any capture itself.
 */
final class SystemUiKeyguardGoneSource {
    private static final String REPOSITORY =
            "com.android.systemui.keyguard.data.repository.KeyguardTransitionRepositoryImpl";
    private static final String TRANSITION_STEP =
            "com.android.systemui.keyguard.shared.model.TransitionStep";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean GONE_FINISHED_SENT = new AtomicBoolean();

    private SystemUiKeyguardGoneSource() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> repositoryClass = Class.forName(REPOSITORY, false, classLoader);
            Class<?> stepClass = Class.forName(TRANSITION_STEP, false, classLoader);
            int hooked = 0;
            for (Method method : repositoryClass.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())) continue;
                if (!"emitTransition".equals(method.getName())) continue;
                if (!containsParameter(method, stepClass)) continue;
                HookUtil.hook(method, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object step = findStep(args, stepClass);
                    Object result = chain.proceed(args);
                    try {
                        if (step != null) onTransitionStep(step);
                    } catch (Throwable error) {
                        // Never let LiquidDock observation failures escape into SystemUI's keyguard
                        // transition path. Even diagnostic logging is best-effort only.
                        try {
                            Api101Bridge.log("[DC] SystemUI unlock observer failed", error);
                        } catch (Throwable ignored) {
                            // SystemUI has already completed the original method; preserve that result.
                        }
                    }
                    return result;
                });
                hooked++;
            }
            if (hooked == 0) {
                INSTALLED.set(false);
                throw new IllegalStateException(
                        "KeyguardTransitionRepositoryImpl.emitTransition(TransitionStep) unavailable");
            }
            Api101Bridge.log("[DC] SystemUI keyguard GONE FINISHED source installed hooks=" + hooked);
        } catch (Throwable error) {
            INSTALLED.set(false);
            Api101Bridge.log("[DC] SystemUI keyguard GONE FINISHED source unavailable", error);
        }
    }

    private static boolean containsParameter(Method method, Class<?> type) {
        for (Class<?> parameter : method.getParameterTypes()) {
            if (parameter == type) return true;
        }
        return false;
    }

    private static Object findStep(Object[] args, Class<?> type) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg != null && type.isInstance(arg)) return arg;
        }
        return null;
    }

    private static void onTransitionStep(Object step) {
        String from = token(read(step, "getFrom", "from"));
        String to = token(read(step, "getTo", "to"));
        String state = token(read(step, "getTransitionState", "transitionState"));
        if (!SystemUiKeyguardGonePolicy.isGoneTransitionAttempt(from, to)) return;

        if (!SystemUiKeyguardGonePolicy.shouldPublishFinished(from, to, state)) {
            // Any new real transition into GONE allows its later FINISHED step to publish once.
            GONE_FINISHED_SENT.set(false);
            return;
        }
        if (!GONE_FINISHED_SENT.compareAndSet(false, true)) return;
        publishFinished(from);
    }

    private static Object read(Object owner, String getter, String field) {
        HookUtil.InvocationResult<Object> getterResult = HookUtil.tryInvoke(owner, getter);
        Object value = getterResult.succeeded() ? getterResult.value() : null;
        if (value != null) return value;
        try {
            return HookUtil.getField(owner, field);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String token(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static void publishFinished(String from) {
        HookUtil.InvocationResult<Object> applicationResult =
                HookUtil.tryInvokeActivityThreadCurrentApplication();
        Object application = applicationResult.succeeded() ? applicationResult.value() : null;
        if (!(application instanceof Context)) {
            GONE_FINISHED_SENT.set(false);
            Api101Bridge.log("[DC] SystemUI keyguard GONE FINISHED publish skipped: no application");
            return;
        }
        try {
            Intent intent = new Intent(SystemUiKeyguardGoneProtocol.ACTION)
                    .setPackage(SystemUiKeyguardGoneProtocol.LAUNCHER_PACKAGE)
                    .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
            ((Context) application).sendBroadcast(intent);
            Api101Bridge.log("[DC] SystemUI " + from + "->GONE FINISHED published");
        } catch (Throwable error) {
            GONE_FINISHED_SENT.set(false);
            Api101Bridge.log("[DC] SystemUI keyguard GONE FINISHED publish unavailable", error);
        }
    }
}
