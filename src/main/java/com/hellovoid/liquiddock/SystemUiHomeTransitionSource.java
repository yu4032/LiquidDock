package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Read-only observer for the real WMShell HOME transition lifecycle in MiuiSystemUI.
 *
 * <p>The decompiled HomeTransitionObserver already owns HOME classification. LiquidDock deliberately
 * does not parse TransitionInfo itself: it records notifyHomeVisibilityChanged(boolean) only while
 * the observer's own onTransitionReady(token, ...) is executing, then carries that classification
 * to the same token's onTransitionStarting/onTransitionFinished callbacks.</p>
 */
final class SystemUiHomeTransitionSource {
    private static final String TAG = "[DC][SystemUiHomeTiming]";
    private static final String HOME_OBSERVER =
            "com.android.wm.shell.transition.HomeTransitionObserver";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final SystemUiHomeTransitionTracker tracker = new SystemUiHomeTransitionTracker();

    private SystemUiHomeTransitionSource() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> observer = Class.forName(HOME_OBSERVER, false, classLoader);
            Method ready = findInstanceMethod(observer, "onTransitionReady", 4);
            Method notify = findInstanceMethod(observer, "notifyHomeVisibilityChanged", 1);
            Method starting = findInstanceMethod(observer, "onTransitionStarting", 1);
            Method finished = findInstanceMethod(observer, "onTransitionFinished", 2);
            Method merged = findInstanceMethod(observer, "onTransitionMerged", 2);

            HookUtil.hook(ready, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object token = args.length > 0 ? args[0] : null;
                tracker.beginReady(token);
                try {
                    return chain.proceed(args);
                } finally {
                    tracker.endReady();
                }
            });

            HookUtil.hook(notify, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (args.length > 0 && args[0] instanceof Boolean) {
                    tracker.recordCurrentReadyVisibility((Boolean) args[0]);
                }
                return chain.proceed(args);
            });

            HookUtil.hook(starting, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object token = args.length > 0 ? args[0] : null;
                try {
                    SystemUiHomeTransitionTracker.Event event = tracker.onStarting(token);
                    if (event != null) publish(SystemUiHomeTransitionProtocol.PHASE_START,
                            event, false);
                } catch (Throwable error) {
                    log("start publish failed", error);
                }
                return result;
            });

            HookUtil.hook(finished, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object token = args.length > 0 ? args[0] : null;
                boolean aborted = args.length > 1 && args[1] instanceof Boolean
                        && (Boolean) args[1];
                try {
                    SystemUiHomeTransitionTracker.Event event = tracker.onFinished(token);
                    if (event != null) publish(SystemUiHomeTransitionProtocol.PHASE_FINISH,
                            event, aborted);
                } catch (Throwable error) {
                    log("finish publish failed", error);
                }
                return result;
            });

            HookUtil.hook(merged, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object source = args.length > 0 ? args[0] : null;
                Object target = args.length > 1 ? args[1] : null;
                try {
                    tracker.onMerged(source, target);
                } catch (Throwable error) {
                    log("merge tracking failed", error);
                }
                return result;
            });

            Api101Bridge.log(TAG + " installed on " + HOME_OBSERVER);
        } catch (Throwable error) {
            INSTALLED.set(false);
            Api101Bridge.log(TAG + " unavailable", error);
        }
    }

    private static Method findInstanceMethod(Class<?> owner, String name, int parameterCount)
            throws NoSuchMethodException {
        for (Method method : owner.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) continue;
            if (!name.equals(method.getName())) continue;
            if (method.getParameterCount() != parameterCount) continue;
            method.setAccessible(true);
            return method;
        }
        throw new NoSuchMethodException(owner.getName() + "#" + name + "/" + parameterCount);
    }

    private static void publish(int phase, SystemUiHomeTransitionTracker.Event event,
                                boolean aborted) {
        HookUtil.InvocationResult<Object> applicationResult =
                HookUtil.tryInvokeActivityThreadCurrentApplication();
        Object application = applicationResult.succeeded() ? applicationResult.value() : null;
        if (!(application instanceof Context)) {
            log("publish skipped: no application", null);
            return;
        }
        long eventTimeNanos = SystemClock.elapsedRealtimeNanos();
        try {
            Intent intent = new Intent(SystemUiHomeTransitionProtocol.ACTION)
                    .setPackage(SystemUiHomeTransitionProtocol.LAUNCHER_PACKAGE)
                    .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
                    .putExtra(SystemUiHomeTransitionProtocol.EXTRA_PHASE, phase)
                    .putExtra(SystemUiHomeTransitionProtocol.EXTRA_HOME_VISIBLE,
                            event.homeVisible())
                    .putExtra(SystemUiHomeTransitionProtocol.EXTRA_SERIAL, event.serial())
                    .putExtra(SystemUiHomeTransitionProtocol.EXTRA_EVENT_TIME_NANOS,
                            eventTimeNanos)
                    .putExtra(SystemUiHomeTransitionProtocol.EXTRA_ABORTED, aborted);
            ((Context) application).sendBroadcast(intent);
            Api101Bridge.log(TAG + " phase=" + phase
                    + " homeVisible=" + event.homeVisible()
                    + " serial=" + event.serial()
                    + " t=" + eventTimeNanos
                    + " aborted=" + aborted);
        } catch (Throwable error) {
            log("publish unavailable", error);
        }
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message, error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {
            // Observation must never escape into SystemUI's transition path.
        }
    }
}
