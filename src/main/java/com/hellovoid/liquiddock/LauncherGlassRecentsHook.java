package com.hellovoid.liquiddock;

/** Uses HyperOS's semantic Recents dispatcher instead of guessing state from a mounted View. */
final class LauncherGlassRecentsHook {
    private static final String TAG = "[DC][GlassScene]";
    private static final String RECENTS_DISPATCHER =
            "com.miui.home.recents.RecentsServiceDispatcher";
    private static boolean installed;

    private LauncherGlassRecentsHook() {}

    static void install(ClassLoader classLoader, LiquidDockConfig config) {
        if (installed || config == null || !config.enabled || !config.glass.enabled) return;
        try {
            HookUtil.hookMethod(classLoader, RECENTS_DISPATCHER, "onRecentViewShow", chain -> {
                // Hide Workspace glass before the vendor starts dispatching the visible Recents state.
                LauncherGlassSceneController.setRecentsCoveredForAll(true);
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hookMethod(classLoader, RECENTS_DISPATCHER, "onRecentViewHide", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                // Reveal only after the vendor has left Recents; the controller still requires a fresh frame.
                LauncherGlassSceneController.setRecentsCoveredForAll(false);
                return result;
            });
            installed = true;
            MainHook.log(TAG + " semantic Recents dispatcher hooks installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " semantic Recents dispatcher unavailable: " + error);
        }
    }
}
