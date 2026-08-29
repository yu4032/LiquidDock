package com.hellovoid.liquiddock;

/** Recomputes Dock glass after HyperOS commits the final hotseat drop geometry. */
final class DockGlassDropRefreshHook {
    private static final String TAG = "[DC][DockGlass]";
    private static final String HOTSEATS =
            "com.miui.home.launcher.hotseats.HotSeatsListContent";
    private static boolean installed;

    private DockGlassDropRefreshHook() {}

    static void install(ClassLoader classLoader) {
        if (installed) return;
        try {
            HookUtil.hookMethod(classLoader, HOTSEATS, "onDropAnimationFinish", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Miuix307ZeroCopyRenderer.requestDockSceneRefresh();
                return result;
            });
            installed = true;
            MainHook.log(TAG + " hotseat drop-finish refresh hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " hotseat drop-finish refresh hook unavailable: " + error);
        }
    }
}
