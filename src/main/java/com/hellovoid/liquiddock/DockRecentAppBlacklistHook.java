package com.hellovoid.liquiddock;

import android.content.Context;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Removes blacklisted apps from the vendor recommendation input pool before
 * HotSeatsListRecentsAppProvider performs its own selection/ranking.
 */
final class DockRecentAppBlacklistHook {
    private static final String PROVIDER =
            "com.miui.home.launcher.hotseats.HotSeatsListRecentsAppProvider";

    private DockRecentAppBlacklistHook() {}

    static void install(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, PROVIDER, "updateRecommendApp", chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (args.length == 0 || !(args[0] instanceof List)) {
                    return chain.proceed(args);
                }

                @SuppressWarnings("unchecked")
                List<Object> candidates = (List<Object>) args[0];

                Context context = findContext(chain.getThisObject());
                if (context != null) {
                    // Publish the unfiltered provider input. The settings page therefore
                    // reflects apps that can actually enter the vendor recommendation pool.
                    DockRecentAppStore.publishCandidates(context, candidates);
                }

                Set<String> blocked = DockRecentAppStore.readBlacklist();
                if (blocked.isEmpty() || candidates.isEmpty()) {
                    return chain.proceed(args);
                }

                ArrayList<Object> filtered = new ArrayList<>(candidates.size());
                boolean changed = false;
                for (Object item : candidates) {
                    String packageName = DockRecentAppStore.packageNameOf(item);
                    if (!packageName.isEmpty() && blocked.contains(packageName)) {
                        changed = true;
                        continue;
                    }
                    filtered.add(item);
                }

                if (changed) {
                    args[0] = filtered;
                }
                return chain.proceed(args);
            });
            MainHook.log("[DC][DockRecentBlacklist] provider-input filter installed");
        } catch (Throwable error) {
            MainHook.log("[DC][DockRecentBlacklist] unavailable: " + error);
        }
    }

    private static Context findContext(Object provider) {
        // The provider itself is not a View. Prefer Application context already available
        // in the Launcher process instead of introducing another lifecycle hook.
        try {
            Object application = HookUtil.tryInvokeStatic(
                    "com.miui.home.launcher.Application",
                    "getLauncher"
            ).value();
            if (application instanceof View) {
                return ((View) application).getContext().getApplicationContext();
            }
            if (application instanceof Context) {
                return ((Context) application).getApplicationContext();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
