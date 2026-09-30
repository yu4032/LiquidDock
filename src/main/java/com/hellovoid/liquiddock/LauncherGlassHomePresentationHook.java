package com.hellovoid.liquiddock;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Gates Workspace wallpaper capture across Launcher HOME and keyguard presentation boundaries. */
final class LauncherGlassHomePresentationHook {
    private static final String TAG = "[DC][GlassScene]";
    private static final String WINDOW_ELEMENT = "com.miui.home.recents.anim.WindowElement";
    private static final String RECTF_PARAMS = "com.miui.home.recents.anim.RectFParams";
    private static final String RECTF_SPRING_ANIM =
            "com.miui.home.recents.util.RectFSpringAnim";
    private static final String RECTF_SPRING_LISTENER =
            "com.miui.home.recents.util.RectFSpringAnim$RectFSpringAnimListener";
    private static final String CLOSE_TO_HOME = "CLOSE_TO_HOME";
    private static final String CLOSE_TO_HOME_CENTER = "CLOSE_TO_HOME_CENTER";
    private static final String UNLOCK_STATE =
            "com.miui.home.launcher.common.UnlockAnimationStateMachine";
    private static final String PREPARE = "PREPARE";
    private static final long UNLOCK_CAPTURE_FAIL_OPEN_MS = 2_000L;

    private static boolean installed;
    private static volatile long unlockBarrierSerial = -1L;
    private static volatile long unlockBarrierStartedAtMs = -1L;
    private static Handler unlockTimeoutHandler;
    private static Handler homeLifecycleHandler;

    /**
     * WindowElement.addListener(RectFSpringAnim) identifies the spring listener owned by Launcher.
     * The HOME barrier starts only from a real spring start or a running spring retarget that
     * Launcher has already accepted; an animTo request alone never owns capture state.
     */
    private static final ThreadLocal<Boolean> CAPTURING_WINDOW_ELEMENT_LISTENER =
            new ThreadLocal<>();
    private static final Set<Class<?>> HOOKED_WINDOW_ELEMENT_LISTENER_CLASSES =
            ConcurrentHashMap.newKeySet();
    private static final WeakHashMap<Object, Object> WINDOW_ELEMENT_BY_SPRING =
            new WeakHashMap<>();

    private static final LauncherHomeTransitionState HOME_STATE =
            new LauncherHomeTransitionState();
    private static final UnlockCaptureRecoveryState UNLOCK_RECOVERY =
            new UnlockCaptureRecoveryState();

    private LauncherGlassHomePresentationHook() {}

    static void install(ClassLoader classLoader) {
        if (installed) return;
        hookHomeSpringLifecycle(classLoader);
        hookUnlockState(classLoader);
        LauncherWidgetTransitionHook.install(classLoader);
        installed = true;
    }

    /**
     * Launcher 4.50 owns HOME presentation with WindowElement + RectFSpringAnim. The non-running
     * path emits the listener's real onAnimationStart. The already-running path instead retargets
     * the same spring through runningAnimUpdate(), so both accepted boundaries are observed.
     */
    private static void hookHomeSpringLifecycle(ClassLoader classLoader) {
        try {
            Class<?> windowElement = Class.forName(WINDOW_ELEMENT, false, classLoader);
            Class<?> rectParams = Class.forName(RECTF_PARAMS, false, classLoader);
            Class<?> rectAnim = Class.forName(RECTF_SPRING_ANIM, false, classLoader);
            Class<?> listenerInterface = Class.forName(
                    RECTF_SPRING_LISTENER, false, classLoader);

            Method windowAddListener = HookUtil.findMethodExact(
                    windowElement, "addListener", new Class<?>[]{rectAnim});
            Method runningAnimUpdate = HookUtil.findMethodExact(
                    windowElement, "runningAnimUpdate", new Class<?>[]{rectParams});
            Method finishTransition = HookUtil.findMethodExact(
                    windowElement, "finishTransition",
                    new Class<?>[]{boolean.class, boolean.class});
            Method finishCompleted = HookUtil.findMethodExact(
                    windowElement, "onFinishCompleted", new Class<?>[0]);
            Method addSpringListener = HookUtil.findMethodExact(
                    rectAnim, "addAnimatorListener", new Class<?>[]{listenerInterface});

            HookUtil.hook(addSpringListener, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (Boolean.TRUE.equals(CAPTURING_WINDOW_ELEMENT_LISTENER.get())
                        && args.length == 1
                        && listenerInterface.isInstance(args[0])) {
                    installWindowElementSpringListenerHook(args[0], rectAnim);
                }
                return chain.proceed(args);
            });

            HookUtil.hook(windowAddListener, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (args.length > 0) {
                    rememberSpringOwner(chain.getThisObject(), args[0]);
                }
                CAPTURING_WINDOW_ELEMENT_LISTENER.set(Boolean.TRUE);
                try {
                    return chain.proceed(args);
                } finally {
                    CAPTURING_WINDOW_ELEMENT_LISTENER.remove();
                }
            });

            HookUtil.hook(runningAnimUpdate, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object owner = chain.getThisObject();
                Object animation = readWindowElementAnimation(owner);
                Object params = args.length > 0 ? args[0] : null;
                rememberSpringOwner(owner, animation);
                onLauncherRunningAnimationAccepted(
                        owner, animation, readParamsAnimationType(params));
                return result;
            });

            HookUtil.hook(finishTransition, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                HOME_STATE.onOwnerFinishRequested(chain.getThisObject());
                return chain.proceed(args);
            });

            HookUtil.hook(finishCompleted, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                onLauncherOwnerFinishCompleted(chain.getThisObject());
                return result;
            });

            MainHook.log(TAG + " HOME spring lifecycle authority installed");
        } catch (Throwable error) {
            // Fail open: an unavailable HOME lifecycle must never leave Workspace capture blocked.
            MainHook.log(TAG + " HOME spring lifecycle authority unavailable: " + error);
        }
    }

    private static void installWindowElementSpringListenerHook(Object listener, Class<?> rectAnim) {
        if (listener == null || rectAnim == null) return;
        Class<?> listenerClass = listener.getClass();
        if (!HOOKED_WINDOW_ELEMENT_LISTENER_CLASSES.add(listenerClass)) return;
        try {
            Method onAnimationStart = HookUtil.findMethodExact(
                    listenerClass, "onAnimationStart", new Class<?>[]{rectAnim});
            Method onAnimationEnd = HookUtil.findMethodExact(
                    listenerClass, "onAnimationEnd", new Class<?>[]{rectAnim});
            Method onAnimationCancel = HookUtil.findMethodExact(
                    listenerClass, "onAnimationCancel", new Class<?>[]{rectAnim});

            HookUtil.hook(onAnimationStart, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object animation = args.length > 0 ? args[0] : null;
                onLauncherSpringStarted(springOwner(animation), animation);
                return result;
            });
            HookUtil.hook(onAnimationEnd, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                onLauncherSpringPhysicalTerminal(
                        args.length > 0 ? args[0] : null);
                return result;
            });
            HookUtil.hook(onAnimationCancel, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                onLauncherSpringPhysicalTerminal(
                        args.length > 0 ? args[0] : null);
                return result;
            });
            MainHook.log(TAG + " HOME WindowElement spring listener bound structurally");
        } catch (Throwable error) {
            HOOKED_WINDOW_ELEMENT_LISTENER_CLASSES.remove(listenerClass);
            MainHook.log(TAG + " HOME WindowElement spring listener bind failed: " + error);
        }
    }

    private static void onLauncherSpringStarted(Object owner, Object animation) {
        String animType = readAnimationType(animation);
        if (!isHomeCloseType(animType)) return;

        LauncherHomeTransitionState.Decision decision =
                HOME_STATE.onHomeAnimationStarted(owner, animation);
        if (!decision.freezeBarrier) return;

        runHomeLifecycleOnMain(() -> {
            if (!HOME_STATE.isArmed()) return;
            Miuix307ZeroCopyRenderer.onHomeOpeningStarted();
            LauncherGlassSceneController.setHomeTransitionPendingForAll(true);
            LauncherWidgetTransitionCoordinator.onHomeOpeningStarted();
        });
    }

    private static void onLauncherRunningAnimationAccepted(
            Object owner, Object animation, String animType) {
        if (isHomeCloseType(animType)) {
            onLauncherSpringStarted(owner, animation);
            return;
        }
        releaseHomeBarrierOnMain(HOME_STATE.onAnimationRetargetedAway(owner, animation));
    }

    private static void onLauncherSpringPhysicalTerminal(Object animation) {
        HOME_STATE.onSpringPhysicalTerminal(animation);
    }

    private static void onLauncherOwnerFinishCompleted(Object owner) {
        releaseHomeBarrierOnMain(HOME_STATE.onOwnerFinishCompleted(owner));
    }

    private static void releaseHomeBarrierOnMain(
            LauncherHomeTransitionState.Decision decision) {
        if (decision == null || !decision.releaseBarrier) return;
        postHomeLifecycleOnMain(() -> {
            // A newer HOME cycle may have started before an older terminal reached the UI queue.
            if (HOME_STATE.isArmed()) return;
            Miuix307ZeroCopyRenderer.onHomeOpeningFinished();
            LauncherGlassSceneController.setHomeTransitionPendingForAll(false);
            LauncherWidgetTransitionCoordinator.onHomeBarrierReleased();
        });
    }

    private static void postHomeLifecycleOnMain(Runnable task) {
        if (task == null) return;
        homeLifecycleHandler().post(task);
    }

    private static void runHomeLifecycleOnMain(Runnable task) {
        if (task == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
            return;
        }
        homeLifecycleHandler().post(task);
    }

    private static Handler homeLifecycleHandler() {
        synchronized (LauncherGlassHomePresentationHook.class) {
            if (homeLifecycleHandler == null) {
                homeLifecycleHandler = new Handler(Looper.getMainLooper());
            }
            return homeLifecycleHandler;
        }
    }

    private static Object readWindowElementAnimation(Object owner) {
        if (owner == null) return null;
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(owner, "getAnim");
        if (!result.succeeded()) {
            MainHook.log(TAG + " HOME WindowElement spring unavailable: " + result.failure());
            return null;
        }
        return result.value();
    }

    private static String readParamsAnimationType(Object params) {
        if (params == null) return null;
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(params, "getAnimType");
        if (!result.succeeded()) {
            MainHook.log(TAG + " HOME running-update type unavailable: " + result.failure());
            return null;
        }
        return enumName(result.value());
    }

    private static String readAnimationType(Object animation) {
        if (animation == null) return null;
        HookUtil.InvocationResult<Object> result =
                HookUtil.tryInvoke(animation, "getLastAminType");
        if (!result.succeeded()) {
            MainHook.log(TAG + " HOME spring type unavailable: " + result.failure());
            return null;
        }
        return enumName(result.value());
    }

    private static String enumName(Object value) {
        if (value instanceof Enum<?>) return ((Enum<?>) value).name();
        return value != null ? String.valueOf(value) : null;
    }

    private static boolean isHomeCloseType(String animType) {
        return CLOSE_TO_HOME.equals(animType) || CLOSE_TO_HOME_CENTER.equals(animType);
    }

    private static synchronized void rememberSpringOwner(Object owner, Object animation) {
        if (owner == null || animation == null) return;
        WINDOW_ELEMENT_BY_SPRING.put(animation, owner);
    }

    private static synchronized Object springOwner(Object animation) {
        return animation != null ? WINDOW_ELEMENT_BY_SPRING.get(animation) : null;
    }

    /**
     * Launcher PREPARE is the early freeze signal. SystemUI FINISHED normally starts endpoint
     * rollover; a bounded fail-open only releases a barrier whose exact serial is still current.
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
            MainHook.log(TAG + " unlock freeze installed PREPARE; release=SystemUI FINISHED/timeout");
        } catch (Throwable error) {
            MainHook.log(TAG + " unlock PREPARE freeze unavailable: " + error);
        }
    }

    /** Normal unlock capture boundary: SystemUI LOCKSCREEN -> GONE FINISHED. */
    static void onSystemUiLockscreenGoneFinished() {
        UnlockCaptureRecoveryState.Decision decision = UNLOCK_RECOVERY.onSystemUiGoneFinished();
        applyUnlockDecision(decision, decision.suspendProducers
                ? "SystemUI/FINISHED-failsafe-arm"
                : "SystemUI LOCKSCREEN->GONE FINISHED");
    }

    static boolean isUnlockCaptureBlocked() {
        if (!UNLOCK_RECOVERY.isBlocked()) return false;
        long serial = unlockBarrierSerial;
        long startedAtMs = unlockBarrierStartedAtMs;
        if (serial > 0L && startedAtMs >= 0L
                && SystemClock.elapsedRealtime() - startedAtMs >= UNLOCK_CAPTURE_FAIL_OPEN_MS) {
            failOpenUnlockBarrierIfCurrent(serial, "gate-age");
        }
        return UNLOCK_RECOVERY.isBlocked();
    }

    private static void applyUnlockDecision(
            UnlockCaptureRecoveryState.Decision decision, String reason) {
        if (decision == null) return;
        if (decision.suspendProducers) {
            LauncherGlassSceneController.setUnlockTransitionPendingForAll(true);
            LauncherGlassSessionRegistry.suspendForUnlockCapture();
            armUnlockFailOpen(decision.serial);
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

    private static void armUnlockFailOpen(long serial) {
        unlockBarrierSerial = serial;
        unlockBarrierStartedAtMs = SystemClock.elapsedRealtime();
        timeoutHandler().postDelayed(
                () -> failOpenUnlockBarrierIfCurrent(serial, "PREPARE-timeout"),
                UNLOCK_CAPTURE_FAIL_OPEN_MS);
    }

    private static void failOpenUnlockBarrierIfCurrent(long serial, String trigger) {
        UnlockCaptureRecoveryState.Decision timedOut = UNLOCK_RECOVERY.onBarrierTimeout(serial);
        if (!timedOut.releaseBarrier) return;
        MainHook.log(TAG + " unlock capture timeout fail-open trigger=" + trigger
                + " serial=" + serial + " ageMs="
                + Math.max(0L, SystemClock.elapsedRealtime() - unlockBarrierStartedAtMs));
        finishUnlockBarrierNow("unlock-timeout/" + trigger, timedOut.serial);
    }

    private static Handler timeoutHandler() {
        synchronized (LauncherGlassHomePresentationHook.class) {
            if (unlockTimeoutHandler == null) {
                unlockTimeoutHandler = new Handler(Looper.getMainLooper());
            }
            return unlockTimeoutHandler;
        }
    }

    private static void finishUnlockBarrierNow(String reason, long serial) {
        if (unlockBarrierSerial == serial) {
            unlockBarrierSerial = -1L;
            unlockBarrierStartedAtMs = -1L;
        }
        MainHook.log(TAG + " unlock wallpaper capture released: " + reason
                + " serial=" + serial);
        // SceneController requests a fresh generation before exposing Workspace glass again.
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
