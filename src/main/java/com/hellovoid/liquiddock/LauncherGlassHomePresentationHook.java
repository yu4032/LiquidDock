package com.hellovoid.liquiddock;

import android.content.Context;
import android.os.SystemClock;

/** Gates Workspace wallpaper capture across HOME and keyguard presentation boundaries. */
final class LauncherGlassHomePresentationHook {
    private static final String TAG = "[DC][GlassScene]";
    private static final String WINDOW_ELEMENT = "com.miui.home.recents.anim.WindowElement";
    private static final String HOME_END_CALLBACK =
            "com.miui.home.recents.anim.WindowElement$mRectFSpringAnimListener$1";
    private static final String CLOSE_TO_HOME = "CLOSE_TO_HOME";
    private static final String CLOSE_TO_HOME_CENTER = "CLOSE_TO_HOME_CENTER";
    private static final String UNLOCK_STATE =
            "com.miui.home.launcher.common.UnlockAnimationStateMachine";
    private static final String PREPARE = "PREPARE";

    private static boolean installed;
    private static boolean homeTransitionArmed;
    private static volatile boolean unlockTransitionArmed;
    private static long unlockTransitionSerial;
    private static long unlockReleaseScheduledSerial = -1L;

    // WMShell HomeTransitionObserver emits this at Transitions#onTransitionReady. It is a visual
    // timing hint only: capture freshness remains owned by LauncherGlassSceneController.
    private static boolean systemUiHomeVisibleReady;
    private static long systemUiHomeVisibleSourceUptimeMs;
    private static long lastSystemUiRevealSourceUptimeMs = -1L;

    private LauncherGlassHomePresentationHook() {}

    static void install(ClassLoader classLoader) {
        if (installed) return;
        hookHomeStart(classLoader);
        hookHomeEnd(classLoader);
        hookUnlockState(classLoader);
        installed = true;
    }

    private static void hookHomeStart(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, WINDOW_ELEMENT, "animTo", chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                boolean closingToHome = containsHomeClose(args);
                if (closingToHome) {
                    homeTransitionArmed = true;
                    // Freeze producer/freshness first. The cached visual may be revealed only after
                    // the vendor HOME animation has actually been entered.
                    LauncherGlassSceneController.setHomeTransitionPendingForAll(true);
                }
                Object result = chain.proceed(args);
                if (closingToHome) {
                    maybeRevealFromSystemUi("Launcher/animTo");
                }
                return result;
            }, Object.class);
        } catch (Throwable error) {
            MainHook.log(TAG + " HOME presentation start unavailable: " + error);
        }
    }

    private static void hookHomeEnd(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, HOME_END_CALLBACK, "onAnimationEnd", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (homeTransitionArmed) {
                    homeTransitionArmed = false;
                    LauncherGlassSceneController.setHomeTransitionPendingForAll(false);
                }
                return result;
            }, "com.miui.home.recents.util.RectFSpringAnim");
        } catch (Throwable error) {
            MainHook.log(TAG + " HOME presentation end unavailable: " + error);
        }
    }

    static void onSystemUiHomeVisibilityReady(
            boolean visible, long sourceUptimeMs, long receiveUptimeMs) {
        systemUiHomeVisibleReady = visible;
        if (!visible) {
            systemUiHomeVisibleSourceUptimeMs = 0L;
            lastSystemUiRevealSourceUptimeMs = -1L;
            // HOME is leaving the foreground. Hiding the cached glass immediately is compositor-
            // only and makes the next HOME-visible edge start from alpha 0.
            LauncherGlassStaticLayer.hideFromSystemUiTimingForAll();
            MainHook.log(TAG + " SystemUI HOME hidden-ready");
            return;
        }

        systemUiHomeVisibleSourceUptimeMs = sourceUptimeMs;
        MainHook.log(TAG + " SystemUI HOME visible-ready source=" + sourceUptimeMs
                + " latency=" + Math.max(0L, receiveUptimeMs - sourceUptimeMs)
                + "ms homeArmed=" + homeTransitionArmed
                + " unlockArmed=" + unlockTransitionArmed);
        if (homeTransitionArmed && !unlockTransitionArmed) {
            revealFromSystemUi(sourceUptimeMs, receiveUptimeMs, "SystemUI/ready");
        }
    }

    private static void maybeRevealFromSystemUi(String reason) {
        if (!systemUiHomeVisibleReady || unlockTransitionArmed) return;
        long sourceUptimeMs = systemUiHomeVisibleSourceUptimeMs;
        revealFromSystemUi(sourceUptimeMs, SystemClock.uptimeMillis(), reason);
    }

    private static void revealFromSystemUi(
            long sourceUptimeMs, long receiveUptimeMs, String reason) {
        if (sourceUptimeMs <= 0L || sourceUptimeMs == lastSystemUiRevealSourceUptimeMs) return;
        lastSystemUiRevealSourceUptimeMs = sourceUptimeMs;
        LauncherGlassStaticLayer.revealFromSystemUiTimingForAll(
                sourceUptimeMs, receiveUptimeMs);
        MainHook.log(TAG + " SystemUI HOME reveal consumed reason=" + reason
                + " source=" + sourceUptimeMs
                + " applied=" + receiveUptimeMs);
    }

    private static boolean containsHomeClose(Object[] args) {
        if (args == null) return false;
        for (Object arg : args) {
            String token = String.valueOf(arg);
            if (token.contains(CLOSE_TO_HOME_CENTER) || token.contains(CLOSE_TO_HOME)) return true;
        }
        return false;
    }

    /**
     * Launcher PREPARE is only an early freeze signal. It may hide the glass and pause the
     * zero-copy producer, but it is never allowed to release capture again. The sole release
     * authority is SystemUI's LOCKSCREEN -> GONE FINISHED TransitionStep.
     */
    private static void hookUnlockState(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, UNLOCK_STATE, "setState", chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                String state = args.length > 0 ? String.valueOf(args[0]) : "";
                if (PREPARE.equals(state)) {
                    Object launcher = readField(chain.getThisObject(), "mLauncher");
                    if (launcher instanceof Context) {
                        SystemUiKeyguardGoneRuntime.ensureRegistered((Context) launcher);
                    }
                    armUnlockCapture("Launcher/PREPARE");
                }
                return chain.proceed(args);
            }, "com.miui.home.launcher.common.UnlockAnimationStateMachine$STATE");
            MainHook.log(TAG + " unlock freeze installed PREPARE; release=SystemUI FINISHED only");
        } catch (Throwable error) {
            MainHook.log(TAG + " unlock PREPARE freeze unavailable: " + error);
        }
    }

    private static void armUnlockCapture(String reason) {
        unlockTransitionSerial++;
        unlockReleaseScheduledSerial = -1L;
        unlockTransitionArmed = true;
        LauncherGlassSceneController.setUnlockTransitionPendingForAll(true);
        LauncherGlassSessionRegistry.suspendForUnlockCapture();
        MainHook.log(TAG + " unlock wallpaper capture frozen reason=" + reason
                + " serial=" + unlockTransitionSerial);
    }

    /** Sole unlock capture boundary: SystemUI LOCKSCREEN -> GONE FINISHED. */
    static void onSystemUiLockscreenGoneFinished() {
        if (!unlockTransitionArmed) {
            // PREPARE can be skipped on vendor edge paths. Fail closed before rebuilding so the
            // old lockscreen-backed OES generation can never be reused.
            armUnlockCapture("SystemUI/FINISHED-failsafe-arm");
        }
        final long serial = unlockTransitionSerial;
        if (unlockReleaseScheduledSerial == serial) return;
        unlockReleaseScheduledSerial = serial;

        MainHook.log(TAG + " SystemUI LOCKSCREEN->GONE FINISHED; rebuilding wallpaper endpoint"
                + " serial=" + serial);
        LauncherGlassSessionRegistry.prepareUnlockCaptureReturn(() -> {
            if (!unlockTransitionArmed || serial != unlockTransitionSerial) return;
            finishUnlockBarrierNow("SystemUI LOCKSCREEN->GONE FINISHED/endpoint-rolled");
        });
    }

    static boolean isUnlockCaptureBlocked() {
        return unlockTransitionArmed;
    }

    private static void finishUnlockBarrierNow(String reason) {
        if (!unlockTransitionArmed) return;
        unlockTransitionArmed = false;
        unlockReleaseScheduledSerial = -1L;
        MainHook.log(TAG + " unlock wallpaper capture released: " + reason);
        // SceneController keeps the glass hidden until the first fresh producer generation lands.
        LauncherGlassSceneController.setUnlockTransitionPendingForAll(false);
    }

    private static Object readField(Object target, String name) {
        if (target == null) return null;
        try {
            return HookUtil.getField(target, name);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
