package com.hellovoid.liquiddock;

import android.view.View;

/** Binds Launcher.mOverviewPanel to the Workspace scene visibility state machine. */
final class LauncherGlassRecentsHook {
    private static boolean installed;
    private LauncherGlassRecentsHook() {}
    static void install(ClassLoader classLoader, LiquidDockConfig config) {
        if (installed || config == null || !config.enabled || !config.glass.enabled) return;
        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.Launcher", "setupViews", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                try {
                    Object launcher = chain.getThisObject();
                    Object workspace = HookUtil.getField(launcher, "mWorkspace");
                    Object overview = HookUtil.getField(launcher, "mOverviewPanel");
                    if (workspace instanceof View && overview instanceof View) {
                        LauncherGlassSceneController.bindRecentsView((View) workspace, (View) overview);
                    }
                } catch (Throwable error) {
                    MainHook.log("[DC][GlassScene] recents bind unavailable: " + error);
                }
                return result;
            });
            installed = true;
        } catch (Throwable error) {
            MainHook.log("[DC][GlassScene] recents hook unavailable: " + error);
        }
    }
}
