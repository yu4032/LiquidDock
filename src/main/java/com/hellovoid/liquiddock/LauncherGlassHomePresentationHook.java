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

    // Launcher 4.50 markers stay installed as a fail-open fallback when the SystemUI handoff is
    // unavailable. Once a matching SystemUI HOME START arrives, only SystemUI FINISH may release
    // the HOME capture barrier for that transition.
    private static boolean homeTransitionArmed;
    private static boolean systemUiHomeTransitionArmed;
    private static long activeSystemUiHomeSerial = -1L;
    private static long lastSystemUiHomeEventElapsedNanos = -1L;
    private static long lastLauncherHomeEndElapsedNanos = -1L;

    private static final UnlockCaptureRecoveryState UNLOCK_RECOVERY =
            new UnlockCaptureRecoveryState();

    private LauncherGlassHomePresentationHook() {}

    static void install(ClassLoader classLoader) {
        if (installed) return;
        hookHomeStart(classLoader);
        hookHomeEnd(classLoader);
        hookUnlockState(classLoader);
        LauncherWidgetTransitionHook.install(classLoader);
        installed = true;
    }

    private static void hookHomeStart(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, WINDOW_ELEMENT, "animTo", chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                boolean closeToHome = containsHomeClose(args);
                if (closeToHome) {
                    synchronized (LauncherGlassHomePresentationHook.class) {
                        homeTransitionArmed = true;
                    }
                    // This is the fallback freeze boundary. If SystemUI START was already received,
                    // the flag is already set and this is intentionally idempotent.
                    LauncherGlassSceneController.setHomeTransitionPendingForAll(true);
                    LauncherWidgetTransitionCoordinator.onHomeOpeningStarted();
                }

                Object result = chain.proceed(args);
                if (closeToHome && !isSystemUiHomeAuthorityActive()) {
                    // Fallback only: SystemUI START normally reveals earlier, before WMShell starts
                    // its transition animation. If that cross-process signal is unavailable, retain
                    // the proven Launcher 4.50 WindowElement timing rather than leaving glass hidden.
                    LauncherGlassSceneController.beginHomeReturnRevealForAll();
                    MainHook.log(TAG + " APP HOME reveal started by Launcher fallback");
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
                boolean releaseFallback = false;
                boolean heldForSystemUi = false;
                long endElapsedNanos = SystemClock.elapsedRealtimeNanos();
                synchronized (LauncherGlassHomePresentationHook.class) {
                    if (homeTransitionArmed) {
                        homeTransitionArmed = false;
                        lastLauncherHomeEndElapsedNanos = endElapsedNanos;
                        if (systemUiHomeTransitionArmed) {
                            heldForSystemUi = true;
                        } else {
                            releaseFallback = true;
                        }
                    }
                }
                if (releaseFallback) {
                    LauncherGlassSceneController.setHomeTransitionPendingForAll(false);
                    LauncherWidgetTransitionCoordinator.onHomeBarrierReleased();
                    MainHook.log(TAG + " APP HOME barrier released by Launcher fallback");
                } else if (heldForSystemUi) {
                    MainHook.log(TAG + " APP HOME Launcher end observed; waiting for SystemUI FINISH");
                }
                return result;
            }, "com.miui.home.recents.util.RectFSpringAnim");
        } catch (Throwable error) {
            MainHook.log(TAG + " HOME presentation end unavailable: " + error);
        }
    }

    /**
     * Precise HOME opening boundary from WMShell HomeTransitionObserver.onTransitionStarting.
     * The source timestamp is SystemClock.elapsedRealtimeNanos() in SystemUI, so it can be compared
     * directly with Launcher callback timestamps and stale broadcasts can be rejected even across
     * the process boundary.
     */
    static void onSystemUiHomeTransitionStarted(
            boolean homeVisible, long serial, long eventTimeNanos) {
        if (serial <= 0L || eventTimeNanos <= 0L) return;

        boolean releaseSupersededHome = false;
        synchronized (LauncherGlassHomePresentationHook.class) {
            if (eventTimeNanos <= lastSystemUiHomeEventElapsedNanos) return;
            // A delayed START that was generated before Launcher already completed its fallback
            // animation must not re-arm a finished HOME barrier.
            if (homeVisible && eventTimeNanos <= lastLauncherHomeEndElapsedNanos) return;

            lastSystemUiHomeEventElapsedNanos = eventTimeNanos;
            if (!homeVisible) {
                if (systemUiHomeTransitionArmed) {
                    systemUiHomeTransitionArmed = false;
                    activeSystemUiHomeSerial = -1L;
                    releaseSupersededHome = true;
                }
            } else {
                systemUiHomeTransitionArmed = true;
                activeSystemUiHomeSerial = serial;
            }
        }

        if (!homeVisible) {
            if (releaseSupersededHome) {
                LauncherGlassSceneController.setHomeTransitionPendingForAll(false);
                MainHook.log(TAG + " SystemUI HOME opening superseded by HOME-hidden START"
                        + " serial=" + serial);
            }
            return;
        }

        // Freeze fresh capture first, then fade the already prepared static layer. The cached layer
        // may be presented during the WMShell animation, but requestFreshBackdrop remains blocked by
        // homeTransitionPending until the matching SystemUI FINISH arrives. A returning widget is
        // separately kept at alpha 0 until this new scene generation has actually rendered fresh.
        LauncherGlassSceneController.setHomeTransitionPendingForAll(true);
        LauncherWidgetTransitionCoordinator.onHomeOpeningStarted();
        LauncherGlassSceneController.beginHomeReturnRevealForAll();
        MainHook.log(TAG + " SystemUI HOME START authority serial=" + serial
                + " t=" + eventTimeNanos);
    }

    /** Matching WMShell onTransitionFinished boundary for the active HOME-opening serial. */
    static void onSystemUiHomeTransitionFinished(
            boolean homeVisible, long serial, long eventTimeNanos, boolean aborted) {
        if (serial <= 0L || eventTimeNanos <= 0L) return;

        boolean release = false;
        synchronized (LauncherGlassHomePresentationHook.class) {
            if (eventTimeNanos <= lastSystemUiHomeEventElapsedNanos) return;
            lastSystemUiHomeEventElapsedNanos = eventTimeNanos;
            if (!homeVisible || !systemUiHomeTransitionArmed
                    || serial != activeSystemUiHomeSerial) return;
            systemUiHomeTransitionArmed = false;
            activeSystemUiHomeSerial = -1L;
            release = true;
        }

        if (release) {
            LauncherGlassSceneController.setHomeTransitionPendingForAll(false);
            LauncherWidgetTransitionCoordinator.onHomeBarrierReleased();
            MainHook.log(TAG + " SystemUI HOME FINISH authority serial=" + serial
                    + " t=" + eventTimeNanos + " aborted=" + aborted);
        }
    }

    private static synchronized boolean isSystemUiHomeAuthorityActive() {
        return systemUiHomeTransitionArmed;
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
                    applyUnlockDecision(UNLOCK_RECOVERY.onPrepare(), "Launcher/PREPARE");
                }
                return chain.proceed(args);
            }, "com.miui.home.launcher.common.UnlockAnimationStateMachine$STATE");
            MainHook.log(TAG + " unlock freeze installed PREPARE; release=SystemUI FINISHED only");
        } catch (Throwable error) {
            MainHook.log(TAG + " unlock PREPARE freeze unavailable: " + error);
        }
    }

    /** Sole unlock capture boundary: SystemUI LOCKSCREEN -> GONE FINISHED. */
    static void onSystemUiLockscreenGoneFinished() {
        UnlockCaptureRecoveryState.Decision decision = UNLOCK_RECOVERY.onSystemUiGoneFinished();
        applyUnlockDecision(decision, decision.suspendProducers
                ? "SystemUI/FINISHED-failsafe-arm"
                : "SystemUI LOCKSCREEN->GONE FINISHED");
    }

    static boolean isUnlockCaptureBlocked() {
        return UNLOCK_RECOVERY.isBlocked();
    }

    private static void applyUnlockDecision(
            UnlockCaptureRecoveryState.Decision decision, String reason) {
        if (decision == null) return;
        if (decision.suspendProducers) {
            LauncherGlassSceneController.setUnlockTransitionPendingForAll(true);
            LauncherGlassSessionRegistry.suspendForUnlockCapture();
            MainHook.log(TAG + " unlock wallpaper capture frozen reason=" + reason
                    + " serial=" + decision.serial);
        }
        if (!decision.requestRollover) return;

        final long serial = decision.serial;
        MainHook.log(TAG + " SystemUI LOCKSCREEN->GONE FINISHED; rebuilding wallpaper endpoint"
                + " serial=" + serial);
        LauncherGlassSessionRegistry.prepareUnlockCaptureReturn(success -> {
            UnlockCaptureRecoveryState.Decision finished =
                    UNLOCK_RECOVERY.onRolloverFinished(serial, success);
            if (!finished.releaseBarrier) {
                if (!success) {
                    MainHook.log(TAG + " unlock endpoint rollover failed; capture remains blocked"
                            + " serial=" + serial);
                }
                return;
            }
            finishUnlockBarrierNow(
                    "SystemUI LOCKSCREEN->GONE FINISHED/endpoint-rolled", finished.serial);
        });
    }

    private static void finishUnlockBarrierNow(String reason, long serial) {
        MainHook.log(TAG + " unlock wallpaper capture released: " + reason
                + " serial=" + serial);
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
