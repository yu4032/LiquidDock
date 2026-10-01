package com.hellovoid.liquiddock;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Removes blacklisted apps from the vendor recommendation input pool before
 * HotSeatsListRecentsAppProvider performs its own selection/ranking.
 *
 * Candidate snapshots are observed separately from getRecommendApps() so the
 * settings UI follows the list the Dock actually consumes without mutating it.
 */
final class DockRecentAppBlacklistHook {
    private static final String PROVIDER =
            "com.miui.home.launcher.hotseats.HotSeatsListRecentsAppProvider";
    private static volatile Context launcherContext;

    private DockRecentAppBlacklistHook() {}

    static void install(ClassLoader classLoader) {
        try {
            installInputFilter(classLoader);
            installCandidateObserver(classLoader);
            installContextCapture(classLoader);
            MainHook.log("[DC][DockRecentBlacklist] provider-input filter + candidate observer installed");
        } catch (Throwable error) {
            MainHook.log("[DC][DockRecentBlacklist] unavailable: " + error);
        }
    }

    private static void installInputFilter(ClassLoader classLoader) {
        HookUtil.hookMethod(classLoader, PROVIDER, "updateRecommendApp", chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            if (args.length == 0 || !(args[0] instanceof List)) {
                return chain.proceed(args);
            }

            @SuppressWarnings("unchecked")
            List<Object> candidates = (List<Object>) args[0];
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
    }

    private static void installCandidateObserver(ClassLoader classLoader) {
        HookUtil.hookMethod(classLoader, PROVIDER, "getRecommendApps", chain -> {
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            Context context = launcherContext;
            if (context != null && result instanceof List) {
                DockRecentAppStore.publishCandidates(context, (List<?>) result);
            }
            return result;
        });
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
                            launcherContext = ((android.view.View) self)
                                    .getContext()
                                    .getApplicationContext();
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
        } catch (Throwable error) {
            MainHook.log("[DC][DockRecentBlacklist] context capture unavailable: " + error);
        }
    }
}
