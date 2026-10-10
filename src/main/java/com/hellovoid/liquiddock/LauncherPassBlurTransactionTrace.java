package com.hellovoid.liquiddock;

import android.view.SurfaceControl;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Debug-only transaction attribution for the Launcher shared-root PassBlur investigation.
 * Never mutates a transaction, changes arguments, or binds a Surface producer.
 */
final class LauncherPassBlurTransactionTrace {
    private static final String TAG = "[DC][PBGLTransaction]";
    private static final AtomicInteger REPORTED = new AtomicInteger();
    private static final int MAX_REPORTS = 160;
    private static volatile boolean installed;

    private LauncherPassBlurTransactionTrace() {}

    static void install() {
        if (installed || !MainHook.debugLogging) return;
        try {
            HookUtil.hookMethod(SurfaceControl.Transaction.class, "setUpdateTextureFlag",
                    new Class<?>[]{SurfaceControl.class, boolean.class, float.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (MainHook.debugLogging && args.length == 3
                                && args[0] instanceof SurfaceControl
                                && args[1] instanceof Boolean && args[2] instanceof Number
                                && REPORTED.get() < MAX_REPORTS) {
                            try {
                                float scale = ((Number) args[2]).floatValue();
                                if (Math.abs(scale - 0.25f) < 0.001f
                                        || Math.abs(scale - 1.0f) < 0.001f) {
                                    int event = REPORTED.incrementAndGet();
                                    if (event <= MAX_REPORTS) {
                                        SurfaceControl target = (SurfaceControl) args[0];
                                        MainHook.log(TAG + " event=" + event
                                                + " layerId=" + Miuix307PassBlurBridge.surfaceLayerId(target)
                                                + " targetId=" + System.identityHashCode(target)
                                                + " enabled=" + args[1]
                                                + " scale=" + scale
                                                + " origin=" + relevantCaller());
                                    }
                                }
                            } catch (Throwable ignored) {
                                // Diagnostic observation must not affect OEM or LiquidDock.
                            }
                        }
                        return chain.proceed(args);
                    });
            installed = true;
            MainHook.log(TAG + " debug transaction target trace installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " debug transaction target trace unavailable: " + error);
        }
    }

    private static String relevantCaller() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String name = frame.getClassName();
            if (name.startsWith("com.miui.")
                    || (name.startsWith("com.hellovoid.liquiddock.")
                        && !name.contains("LauncherPassBlurTransactionTrace")
                        && !name.contains("HookUtil")
                        && !name.contains("Api101Bridge"))) {
                return name + "#" + frame.getMethodName() + ":" + frame.getLineNumber();
            }
        }
        return "unknown";
    }
}
