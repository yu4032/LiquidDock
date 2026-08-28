package com.hellovoid.liquiddock;

import android.view.View;

/** Owns the deterministic MAML root-loaded lifecycle boundary used by widget material adapters. */
final class LauncherMamlRootLoadedHook {
    private static final String TAG = "[DC][MamlRootLoaded]";
    private static boolean installed;

    private LauncherMamlRootLoadedHook() {}

    static boolean install(ClassLoader classLoader) {
        if (installed) return true;
        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.maml.component.MamlView", "initMamlview",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        // Launcher 4.50's MamlView.initMamlview assigns mRoot and calls init(),
                        // whose ScreenElementRoot.selfInit() completes before this returns.
                        Object result = chain.proceed(args);
                        Object owner = chain.getThisObject();
                        if (owner instanceof View
                                && owner.getClass().getName().endsWith(".MaMlHostView")
                                && GlassRuntimeState.isWidgetEnabled()
                                && args.length > 1 && args[1] != null) {
                            LauncherMamlBackgroundSuppressor.claimLoadedRoot(
                                    (View) owner, args[1]);
                        }
                        return result;
                    }, android.content.Context.class, "com.miui.maml.ScreenElementRoot");
            installed = true;
            MainHook.log(TAG + " initMamlview loaded-root hook installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " initMamlview loaded-root hook unavailable: " + error);
            return false;
        }
    }
}
