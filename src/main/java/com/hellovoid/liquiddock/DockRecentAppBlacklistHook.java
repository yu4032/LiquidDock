package com.hellovoid.liquiddock;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Filters only the vendor Dock recommendation source; system Recents itself remains untouched. */
final class DockRecentAppBlacklistHook {
    private static final String PROVIDER =
            "com.miui.home.launcher.hotseats.HotSeatsListRecentsAppProvider";
    private static volatile Context launcherContext;

    private DockRecentAppBlacklistHook() {}

    static void install(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, PROVIDER, "getRecommendApps", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (!(result instanceof List)) return result;

                @SuppressWarnings("unchecked")
                List<Object> source = (List<Object>) result;
                Context context = launcherContext;
                if (context != null) DockRecentAppStore.publishCandidates(context, source);

                Set<String> blocked = DockRecentAppStore.readBlacklist();
                if (blocked.isEmpty() || source.isEmpty()) return result;

                ArrayList<Object> filtered = new ArrayList<>(source.size());
                boolean changed = false;
                for (Object item : source) {
                    String packageName = DockRecentAppStore.packageNameOf(item);
                    if (!packageName.isEmpty() && blocked.contains(packageName)) {
                        changed = true;
                        continue;
                    }
                    filtered.add(item);
                }
                return changed ? filtered : result;
            });
            installContextCapture(classLoader);
            MainHook.log("[DC][DockRecentBlacklist] installed");
        } catch (Throwable error) {
            MainHook.log("[DC][DockRecentBlacklist] unavailable: " + error);
        }
    }

    private static void installContextCapture(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(
                    classLoader,
                    "com.miui.home.launcher.hotseats.HotSeatsListContent",
                    "onFinishInflate",
                    chain -> {
                        Object self = chain.getThisObject();
                        if (self instanceof android.view.View) {
                            launcherContext = ((android.view.View) self).getContext()
                                    .getApplicationContext();
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
        } catch (Throwable error) {
            MainHook.log("[DC][DockRecentBlacklist] context capture unavailable: " + error);
        }
    }
}
