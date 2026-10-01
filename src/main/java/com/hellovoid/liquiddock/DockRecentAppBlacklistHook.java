package com.hellovoid.liquiddock;

import android.content.Context;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Filters Dock recent tasks before the vendor provider applies its recommendation limit.
 * Manual candidate discovery observes the provider only after it finishes rebuilding the
 * final recommendation list.
 */
final class DockRecentAppBlacklistHook {
    private static final String PROVIDER =
            "com.miui.home.launcher.hotseats.HotSeatsListRecentsAppProvider";
    private static final String TASK =
            "com.android.systemui.shared.recents.model.Task";
    private static volatile Context launcherContext;

    private DockRecentAppBlacklistHook() {}

    static void install(ClassLoader classLoader) {
        installBlacklistFilter(classLoader);
        installCandidateSnapshot(classLoader);
        installContextCapture(classLoader);
    }

    /**
     * Vendor chain:
     * RecentsModel.getTaskList()
     *   -> filterSupportLaunchPairApp(Task)
     *   -> sort(lastActiveTime)
     *   -> remove Dock duplicates / invalid apps
     *   -> limit(getLimitCount())
     *
     * Extending filterSupportLaunchPairApp therefore removes a blocked task before limit(),
     * allowing the next eligible task to fill the slot naturally.
     */
    private static void installBlacklistFilter(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(
                    classLoader,
                    PROVIDER,
                    "filterSupportLaunchPairApp",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object original = chain.proceed(args);
                        if (!(original instanceof Boolean) || !((Boolean) original)) {
                            return original;
                        }
                        if (args.length == 0 || args[0] == null) return original;

                        Set<String> blocked = DockRecentAppStore.readBlacklist();
                        if (blocked.isEmpty()) return original;

                        for (String packageName : taskPackageNames(chain.getThisObject(), args[0])) {
                            if (blocked.contains(packageName)) {
                                return false;
                            }
                        }
                        return original;
                    },
                    TASK);
            MainHook.log("[DC][DockRecentBlacklist] pre-limit task filter installed");
        } catch (Throwable error) {
            MainHook.log("[DC][DockRecentBlacklist] task filter unavailable: " + error);
        }
    }

    /**
     * updateFinalRecommendTasks() runs after the asynchronous recent-task evaluation has
     * completed. Observing here avoids consuming a one-shot manual discovery request from an
     * early getRecommendApps() call while the provider still contains its startup empty list.
     */
    private static void installCandidateSnapshot(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, PROVIDER, "updateFinalRecommendTasks", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Context context = launcherContext;
                if (context == null) return result;

                HookUtil.InvocationResult<Object> recommend =
                        HookUtil.tryInvoke(chain.getThisObject(), "getRecommendApps");
                Object value = recommend.succeeded() ? recommend.value() : null;
                if (value instanceof List) {
                    DockRecentAppStore.publishCandidates(context, (List<?>) value);
                }
                return result;
            });
            MainHook.log("[DC][DockRecentBlacklist] manual candidate snapshot installed");
        } catch (Throwable error) {
            MainHook.log("[DC][DockRecentBlacklist] candidate snapshot unavailable: " + error);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> taskPackageNames(Object provider, Object task) {
        HookUtil.InvocationResult<Object> result =
                HookUtil.tryInvoke(provider, "getTaskPackageNames", task);
        Object value = result.succeeded() ? result.value() : null;
        if (!(value instanceof List)) return Collections.emptyList();
        return (List<String>) value;
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
            MainHook.log("[DC][DockRecentBlacklist] Launcher context capture installed");
        } catch (Throwable error) {
            MainHook.log("[DC][DockRecentBlacklist] context capture unavailable: " + error);
        }
    }
}
