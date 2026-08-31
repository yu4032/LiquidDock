package com.hellovoid.liquiddock;

/** Gates Workspace capture until HyperOS 4.50 HOME/unlock presentation is actually finished. */
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
    private static final String IDLE = "IDLE";
    private static final String SPRING_CALLBACK =
            "com.miui.home.launcher.compat.UserPresentAnimationCompatV12Spring$1";
    private static final String FOLME_CALLBACK =
            "com.miui.home.launcher.compat.UserPresentAnimationCompatV12Folme$1";
    private static final String SPRING_REMAINING = "mAllAnimationViewNum";
    private static final String FOLME_TOTAL = "mNumOfAnimatedView";
    private static final String FOLME_CURRENT = "mNumOfCurrentAnimatedView";
    private static boolean installed;
    private static boolean homeTransitionArmed;
    private static volatile boolean unlockTransitionArmed;
    private static boolean unlockPresentationComplete;
    private static boolean unlockUserPresentObserved;
    private static long unlockTransitionSerial;
    private static long unlockReleaseScheduledSerial = -1L;

    private LauncherGlassHomePresentationHook() {}

    static void install(ClassLoader classLoader) {
        if (installed) return;
        hookHomeStart(classLoader);
        hookHomeEnd(classLoader);
        hookUnlockState(classLoader);
        hookUnlockUserPresent(classLoader);
        hookUnlockSpringFinish(classLoader);
        hookUnlockFolmeFinish(classLoader, "onComplete");
        hookUnlockFolmeFinish(classLoader, "onCancel");
        installed = true;
    }

    private static void hookHomeStart(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, WINDOW_ELEMENT, "animTo", chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (containsHomeClose(args)) {
                    homeTransitionArmed = true;
                    LauncherGlassSceneController.setHomeTransitionPendingForAll(true);
                }
                return chain.proceed(args);
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

    private static boolean containsHomeClose(Object[] args) {
        if (args == null) return false;
        for (Object arg : args) {
            String token = String.valueOf(arg);
            if (token.contains(CLOSE_TO_HOME_CENTER) || token.contains(CLOSE_TO_HOME)) return true;
        }
        return false;
    }

    /**
     * Launcher 4.50 can reach PREPARE and then decide not to run any user-present animation.
     * The state machine always returns to IDLE after showPresent(), so IDLE is the deterministic
     * escape for that no-animation path. When real Spring/Folme views are active their counters
     * are already non-zero before IDLE is published, and the barrier remains armed for the real
     * animation listener below.
     */
    private static void hookUnlockState(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, UNLOCK_STATE, "setState", chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                String state = args.length > 0 ? String.valueOf(args[0]) : "";
                if (PREPARE.equals(state)) {
                    unlockTransitionSerial++;
                    unlockReleaseScheduledSerial = -1L;
                    unlockPresentationComplete = false;
                    unlockUserPresentObserved = false;
                    unlockTransitionArmed = true;
                    LauncherGlassSceneController.setUnlockTransitionPendingForAll(true);
                    LauncherGlassSessionRegistry.suspendForUnlockCapture();
                }

                Object result = chain.proceed(args);
                if (IDLE.equals(state)) {
                    releaseUnlockIfIdleWithoutAnimation(chain.getThisObject());
                }
                return result;
            }, "com.miui.home.launcher.common.UnlockAnimationStateMachine$STATE");
            MainHook.log(TAG + " unlock state barrier installed PREPARE/IDLE");
        } catch (Throwable error) {
            MainHook.log(TAG + " unlock PREPARE/IDLE unavailable: " + error);
        }
    }

    /**
     * ACTION_USER_PRESENT is delivered only after keyguard dismissal. Launcher forwards that
     * broadcast directly into this state-machine method, so it is the authoritative boundary for
     * when a HOME backdrop may stop seeing the lockscreen wallpaper. It can arrive before or after
     * the Launcher-owned icon animation finishes; capture is released only after both conditions.
     */
    private static void hookUnlockUserPresent(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, UNLOCK_STATE, "onUserPresent", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (unlockTransitionArmed) {
                    unlockUserPresentObserved = true;
                    MainHook.log(TAG + " unlock USER_PRESENT observed serial="
                            + unlockTransitionSerial);
                    tryReleaseUnlockBarrier("USER_PRESENT");
                }
                return result;
            });
            MainHook.log(TAG + " unlock USER_PRESENT capture barrier installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " unlock USER_PRESENT unavailable: " + error);
        }
    }

    /** Non-fold Launcher 4.50 uses V12Spring. Release only after Xiaomi reaches count zero. */
    private static void hookUnlockSpringFinish(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, SPRING_CALLBACK, "onAnimationEnd", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                releaseUnlockWhenSpringComplete(chain.getThisObject());
                return result;
            }, android.animation.Animator.class);
            MainHook.log(TAG + " unlock Spring terminal-count barrier installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " unlock Spring completion unavailable: " + error);
        }
    }

    /** Fold Launcher 4.50 uses Folme. Its listener resets both counts only on the final view. */
    private static void hookUnlockFolmeFinish(ClassLoader classLoader, String method) {
        try {
            HookUtil.hookMethod(classLoader, FOLME_CALLBACK, method, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                releaseUnlockWhenFolmeComplete(chain.getThisObject());
                return result;
            }, Object.class);
            MainHook.log(TAG + " unlock Folme " + method + " terminal-count barrier installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " unlock Folme " + method + " unavailable: " + error);
        }
    }

    private static void releaseUnlockIfIdleWithoutAnimation(Object stateMachine) {
        if (!unlockTransitionArmed) return;
        Object launcher = readField(stateMachine, "mLauncher");
        Object animation = HookUtil.invoke(launcher, "getUserPresentAnimation");
        if (animation == null) {
            markUnlockPresentationComplete("IDLE/no-animation-object");
            return;
        }

        Integer springRemaining = readIntField(animation, SPRING_REMAINING);
        Integer folmeTotal = readIntField(animation, FOLME_TOTAL);
        if (isPositive(springRemaining) || isPositive(folmeTotal)) return;

        if (springRemaining != null || folmeTotal != null) {
            markUnlockPresentationComplete("IDLE/no-active-animation");
        } else {
            // IDLE is the vendor-owned animation endpoint. Counter reflection can be unavailable
            // for some presentation variants; USER_PRESENT still independently gates release.
            markUnlockPresentationComplete("IDLE/counters-unavailable");
        }
    }

    private static void releaseUnlockWhenSpringComplete(Object callback) {
        if (!unlockTransitionArmed) return;
        Object animation = readField(callback, "this$0");
        Integer remaining = readIntField(animation, SPRING_REMAINING);
        if (remaining != null && remaining <= 0) {
            markUnlockPresentationComplete("Spring/all-views-complete");
        }
    }

    private static void releaseUnlockWhenFolmeComplete(Object callback) {
        if (!unlockTransitionArmed) return;
        Object animation = readField(callback, "this$0");
        Integer total = readIntField(animation, FOLME_TOTAL);
        Integer current = readIntField(animation, FOLME_CURRENT);
        if (total != null && current != null && total <= 0 && current <= 0) {
            markUnlockPresentationComplete("Folme/all-views-complete");
        }
    }

    private static void markUnlockPresentationComplete(String reason) {
        if (!unlockTransitionArmed) return;
        unlockPresentationComplete = true;
        MainHook.log(TAG + " unlock presentation animation complete reason=" + reason
                + " userPresent=" + unlockUserPresentObserved
                + " serial=" + unlockTransitionSerial);
        tryReleaseUnlockBarrier(reason);
    }

    private static void tryReleaseUnlockBarrier(String reason) {
        if (!unlockTransitionArmed || !unlockPresentationComplete || !unlockUserPresentObserved) {
            return;
        }
        scheduleUnlockBarrierReleaseAfterFrame(reason + "/user-present");
    }

    private static void scheduleUnlockBarrierReleaseAfterFrame(String reason) {
        if (!unlockTransitionArmed || !unlockPresentationComplete || !unlockUserPresentObserved) {
            return;
        }
        final long serial = unlockTransitionSerial;
        if (unlockReleaseScheduledSerial == serial) return;
        unlockReleaseScheduledSerial = serial;
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        main.post(() -> {
            if (!unlockTransitionArmed || serial != unlockTransitionSerial) return;
            android.view.Choreographer.getInstance().postFrameCallback(frameTimeNanos -> {
                if (!unlockTransitionArmed || serial != unlockTransitionSerial) return;
                unlockReleaseScheduledSerial = -1L;
                LauncherGlassSessionRegistry.prepareUnlockCaptureReturn(() -> {
                    if (!unlockTransitionArmed || serial != unlockTransitionSerial) return;
                    finishUnlockBarrierNow(reason + "/post-animation-frame/endpoint-rolled");
                });
            });
        });
    }

    static boolean isUnlockCaptureBlocked() {
        return unlockTransitionArmed;
    }

    private static void finishUnlockBarrierNow(String reason) {
        if (!unlockTransitionArmed) return;
        unlockTransitionArmed = false;
        unlockPresentationComplete = false;
        unlockUserPresentObserved = false;
        unlockReleaseScheduledSerial = -1L;
        MainHook.log(TAG + " unlock presentation finished: " + reason);
        LauncherGlassSceneController.setUnlockTransitionPendingForAll(false);
    }

    private static boolean isPositive(Integer value) {
        return value != null && value > 0;
    }

    private static Object readField(Object target, String name) {
        if (target == null) return null;
        try {
            return HookUtil.getField(target, name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Integer readIntField(Object target, String name) {
        Object value = readField(target, name);
        return value instanceof Number ? ((Number) value).intValue() : null;
    }
}
