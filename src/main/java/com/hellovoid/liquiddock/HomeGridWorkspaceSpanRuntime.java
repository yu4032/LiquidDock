package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.ref.WeakReference;

/** Read-only Workspace reference for live grid compatibility preflight. */
final class HomeGridWorkspaceSpanRuntime {
    private static WeakReference<View> workspaceRef = new WeakReference<>(null);
    private static boolean installed;

    private HomeGridWorkspaceSpanRuntime() {}

    static void install(ClassLoader classLoader) {
        if (installed) return;
        HookUtil.hookMethod(
                classLoader,
                "com.miui.home.launcher.Launcher",
                "setupViews",
                chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    try {
                        Object candidate = HookUtil.getField(
                                chain.getThisObject(), "mWorkspace");
                        if (candidate instanceof View) {
                            workspaceRef = new WeakReference<>((View) candidate);
                        }
                    } catch (Throwable error) {
                        MainHook.log("[DC][HomeGridPreflight] workspace capture failed: " + error);
                    }
                    return result;
                });
        installed = true;
    }

    static HomeGridWorkspaceSpanPreflight.Result scan(int columns, int rows) {
        return HomeGridWorkspaceSpanPreflight.scanWorkspace(
                workspaceRef.get(), columns, rows);
    }
}
