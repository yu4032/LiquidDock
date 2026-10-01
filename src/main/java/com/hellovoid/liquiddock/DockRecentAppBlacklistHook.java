package com.hellovoid.liquiddock;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
                Object provider = chain.getThisObject();
                List<Object> recentTasks = readList(provider, "getRecentTaskApps");

                Context context = launcherContext;
                if (context != null) {
                    ArrayList<Object> candidates =
                            new ArrayList<>(source.size() + recentTasks.size());
                    candidates.addAll(source);
                    candidates.addAll(recentTasks);
                    DockRecentAppStore.publishCandidates(context, candidates);
                }

                Set<String> blocked = DockRecentAppStore.readBlacklist();
                if (blocked.isEmpty() || source.isEmpty()) return result;

                ArrayList<Object> filtered = new ArrayList<>(source.size());
                LinkedHashSet<String> selectedPackages = new LinkedHashSet<>();
                boolean changed = false;
                for (Object item : source) {
                    String packageName = DockRecentAppStore.packageNameOf(item);
                    if (!packageName.isEmpty() && blocked.contains(packageName)) {
                        changed = true;
                        continue;
                    }
                    filtered.add(item);
                    if (!packageName.isEmpty()) selectedPackages.add(packageName);
                }
                if (!changed) return result;

                // getRecommendApps() is already a bounded list on current Launcher builds.
                // Removing a blocked item here would otherwise consume one Dock slot. Fill
                // the vacated slots from the provider's ordered recent-task candidate pool.
                int targetSize = source.size();
                for (Object item : recentTasks) {
                    if (filtered.size() >= targetSize) break;
                    String packageName = DockRecentAppStore.packageNameOf(item);
                    if (!packageName.isEmpty()) {
                        if (blocked.contains(packageName) || selectedPackages.contains(packageName)) {
                            continue;
                        }
                        selectedPackages.add(packageName);
                    }
                    filtered.add(item);
                }
                return filtered;
            });
            installContextCapture(classLoader);
            MainHook.log("[DC][DockRecentBlacklist] installed");
        } catch (Throwable error) {
            MainHook.log("[DC][DockRecentBlacklist] unavailable: " + error);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> readList(Object target, String method) {
        if (target == null) return java.util.Collections.emptyList();
        HookUtil.InvocationResult<Object> invocation = HookUtil.tryInvoke(target, method);
        Object value = invocation.succeeded() ? invocation.value() : null;
        return value instanceof List
                ? (List<Object>) value
                : java.util.Collections.emptyList();
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
