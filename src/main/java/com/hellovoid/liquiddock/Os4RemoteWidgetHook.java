package com.hellovoid.liquiddock;

import android.appwidget.AppWidgetHostView;
import android.view.View;
import android.widget.RemoteViews;

/**
 * Installs framework-level AppWidgetHostView hooks inside the OS4 AOT Launcher
 * so RemoteViews widgets (e.g. Notes, Personal Assistant, third-party widgets)
 * support manual component discovery, node hiding, and whitening.
 */
final class Os4RemoteWidgetHook {
    private static final String TAG = "[DC][Os4RemoteWidget]";
    private static volatile boolean installed;

    private Os4RemoteWidgetHook() {}

    static void install(ClassLoader classLoader) {
        if (installed) return;
        ClassLoader targetLoader = classLoader != null
                ? classLoader
                : AppWidgetHostView.class.getClassLoader();
        try {
            HookUtil.hookMethod(targetLoader,
                    "android.appwidget.AppWidgetHostView", "updateAppWidget",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object self = chain.getThisObject();
                        if (self instanceof AppWidgetHostView) {
                            handleHostView((AppWidgetHostView) self);
                        }
                        return result;
                    }, RemoteViews.class);
            HookUtil.hookMethod(targetLoader,
                    "android.appwidget.AppWidgetHostView", "onAttachedToWindow",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object self = chain.getThisObject();
                        if (self instanceof AppWidgetHostView) {
                            handleHostView((AppWidgetHostView) self);
                        }
                        return result;
                    });
            installed = true;
            Api101Bridge.log(TAG + " AppWidgetHostView hooks installed");
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " AppWidgetHostView hook failed: " + error);
        }
    }

    private static void handleHostView(AppWidgetHostView host) {
        if (host == null) return;
        LauncherWidgetWhiteningExecutor.release(host);
        LauncherManualDiscoveryBridge.ensureRegistered(host.getContext());
        LauncherWidgetComponentDiscovery.scan(host);
        LauncherWidgetComponentSelectionExecutor.claim(host);
    }
}
