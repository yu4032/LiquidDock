package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Read-only observer for WMShell's already-decoded HOME visibility signal.
 *
 * HyperOS 16.03 HomeTransitionObserver derives this boolean from TransitionInfo during
 * Transitions#onTransitionReady. LiquidDock only mirrors the semantic result and source uptime;
 * it never parses TransitionInfo itself and never mutates WMShell state.
 */
final class SystemUiHomeTransitionSource {
    private static final String HOME_TRANSITION_OBSERVER =
            "com.android.wm.shell.transition.HomeTransitionObserver";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private SystemUiHomeTransitionSource() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            HookUtil.hookMethod(classLoader, HOME_TRANSITION_OBSERVER,
                    "notifyHomeVisibilityChanged", chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        boolean visible = args.length > 0 && Boolean.TRUE.equals(args[0]);
                        long sourceUptimeMs = SystemClock.uptimeMillis();
                        Object result = chain.proceed(args);
                        try {
                            publish(visible, sourceUptimeMs);
                        } catch (Throwable error) {
                            try {
                                Api101Bridge.log("[DC] SystemUI HOME visibility publish failed", error);
                            } catch (Throwable ignored) {
                                // Preserve WMShell's original result even if diagnostics fail.
                            }
                        }
                        return result;
                    }, boolean.class);
            Api101Bridge.log("[DC] SystemUI HomeTransitionObserver timing source installed");
        } catch (Throwable error) {
            INSTALLED.set(false);
            Api101Bridge.log("[DC] SystemUI HomeTransitionObserver timing source unavailable", error);
        }
    }

    private static void publish(boolean visible, long sourceUptimeMs) {
        Object application = HookUtil.invokeStatic(
                "android.app.ActivityThread", "currentApplication");
        if (!(application instanceof Context)) {
            Api101Bridge.log("[DC] SystemUI HOME visibility publish skipped: no application");
            return;
        }
        Intent intent = new Intent(SystemUiHomeTransitionProtocol.ACTION)
                .setPackage(SystemUiHomeTransitionProtocol.LAUNCHER_PACKAGE)
                .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
                .putExtra(SystemUiHomeTransitionProtocol.EXTRA_VISIBLE, visible)
                .putExtra(SystemUiHomeTransitionProtocol.EXTRA_SOURCE_UPTIME_MS, sourceUptimeMs);
        ((Context) application).sendBroadcast(intent);
        Api101Bridge.log("[DC] SystemUI HOME visibility ready visible=" + visible
                + " sourceUptime=" + sourceUptimeMs);
    }
}
