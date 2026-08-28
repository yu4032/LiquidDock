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
    private static final String PRESENT_CALLBACK =
            "com.miui.home.launcher.compat.UserPresentAnimationCompatV12Folme$1";
    private static final String VENDOR_RESET_COUNTER = "resetAnimationViewNum";
    private static boolean installed;
    private static boolean homeTransitionArmed;
    private static boolean unlockTransitionArmed;

    private LauncherGlassHomePresentationHook() {}

    static void install(ClassLoader classLoader) {
        if (installed) return;
        hookHomeStart(classLoader);
        hookHomeEnd(classLoader);
        hookUnlockStart(classLoader);
        hookUnlockFinish(classLoader, "onComplete");
        hookUnlockFinish(classLoader, "onCancel");
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
            });
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
            });
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

    private static void hookUnlockStart(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, UNLOCK_STATE, "setState", chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (args.length > 0 && PREPARE.equals(String.valueOf(args[0]))) {
                    unlockTransitionArmed = true;
                    LauncherGlassSceneController.setUnlockTransitionPendingForAll(true);
                }
                return chain.proceed(args);
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " unlock PREPARE unavailable: " + error);
        }
    }

    private static void hookUnlockFinish(ClassLoader classLoader, String method) {
        try {
            HookUtil.hookMethod(classLoader, PRESENT_CALLBACK, method, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (unlockTransitionArmed) {
                    unlockTransitionArmed = false;
                    LauncherGlassSceneController.setUnlockTransitionPendingForAll(false);
                }
                return result;
            });
            MainHook.log(TAG + " unlock " + method + " barrier installed " + VENDOR_RESET_COUNTER);
        } catch (Throwable error) {
            MainHook.log(TAG + " unlock " + method + " unavailable: " + error);
        }
    }
}
