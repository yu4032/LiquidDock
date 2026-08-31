package com.hellovoid.liquiddock;

/** Gates Workspace glass across HOME presentation boundaries. */
final class LauncherGlassHomePresentationHook {
    private static final String TAG = "[DC][GlassScene]";
    private static final String WINDOW_ELEMENT = "com.miui.home.recents.anim.WindowElement";
    private static final String HOME_END_CALLBACK =
            "com.miui.home.recents.anim.WindowElement$mRectFSpringAnimListener$1";
    private static final String CLOSE_TO_HOME = "CLOSE_TO_HOME";
    private static final String CLOSE_TO_HOME_CENTER = "CLOSE_TO_HOME_CENTER";

    private static boolean installed;
    private static boolean homeTransitionArmed;

    private LauncherGlassHomePresentationHook() {}

    static void install(ClassLoader classLoader) {
        if (installed) return;
        hookHomeStart(classLoader);
        hookHomeEnd(classLoader);
        installed = true;
    }

    private static void hookHomeStart(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, WINDOW_ELEMENT, "animTo", chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                boolean closeToHome = containsHomeClose(args);
                if (closeToHome) {
                    homeTransitionArmed = true;
                    // Freeze capture and force the cached root layer to alpha=0 before the vendor
                    // CLOSE_TO_HOME spring starts. The same semantic boundary is emitted for the
                    // direct taskFromApp=true App -> HOME path on Launcher 4.50.
                    LauncherGlassSceneController.setHomeTransitionPendingForAll(true);
                }
                Object result = chain.proceed(args);
                if (closeToHome) {
                    // Start presentation only after WindowElement has accepted/started the vendor
                    // animation. Capture remains blocked by homeTransitionPending until the real
                    // RectFSpringAnim onAnimationEnd callback below.
                    LauncherGlassSceneController.beginHomeReturnRevealForAll();
                    MainHook.log(TAG + " APP HOME CLOSE_TO_HOME reveal started");
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

    private static boolean containsHomeClose(Object[] args) {
        if (args == null) return false;
        for (Object arg : args) {
            String token = String.valueOf(arg);
            if (token.contains(CLOSE_TO_HOME_CENTER) || token.contains(CLOSE_TO_HOME)) return true;
        }
        return false;
    }
}
