package com.hellovoid.liquiddock;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Contract for Dock recent-app blacklist and Launcher -> settings candidate snapshots. */
public final class DockRecentAppStore {
    public static final String BLACKLIST_KEY = "dock_recent_app_blacklist";
    public static final String CANDIDATE_PREFS = "dock_recent_app_candidates";
    public static final String CANDIDATE_KEY = "current";
    public static final String DISCOVERY_REQUEST_KEY = "dock_recent_app_discovery_request";
    public static final String ACTION_CANDIDATES =
            "com.hellovoid.liquiddock.DOCK_RECENT_APP_CANDIDATES";
    public static final String EXTRA_PACKAGES = "packages";
    public static final String EXTRA_TOKEN = "token";
    public static final String RECEIVER_CLASS =
            "com.hellovoid.liquiddock.DockRecentAppReceiver";

    private DockRecentAppStore() {}

    static Set<String> readBlacklist() {
        try {
            android.content.SharedPreferences prefs =
                    Api101Bridge.remotePreferences(ConfigReader.REMOTE_GROUP);
            if (prefs == null) return java.util.Collections.emptySet();
            Set<String> value = prefs.getStringSet(BLACKLIST_KEY, null);
            return value == null
                    ? java.util.Collections.emptySet()
                    : new java.util.HashSet<>(value);
        } catch (Throwable ignored) {
            return java.util.Collections.emptySet();
        }
    }

    static void publishCandidates(Context context, List<?> items) {
        if (context == null || items == null) return;
        String token;
        try {
            ConfigReader config = ConfigReader.load();
            if (config.s(DISCOVERY_REQUEST_KEY, "").isEmpty()) return;
            token = config.s(WidgetComponentStore.DISCOVERY_TOKEN_KEY, "");
        } catch (Throwable ignored) {
            return;
        }
        if (token == null || token.isEmpty()) return;

        LinkedHashSet<String> packages = new LinkedHashSet<>();
        for (Object item : items) {
            String packageName = packageNameOf(item);
            if (!packageName.isEmpty()) packages.add(packageName);
        }

        try {
            Intent intent = new Intent(ACTION_CANDIDATES);
            intent.setComponent(new ComponentName(
                    WidgetComponentStore.MODULE_PACKAGE, RECEIVER_CLASS));
            intent.putExtra(EXTRA_TOKEN, token);
            intent.putStringArrayListExtra(EXTRA_PACKAGES, new ArrayList<>(packages));
            context.sendBroadcast(intent);
        } catch (Throwable error) {
            MainHook.log("[DC][DockRecentBlacklist] candidate publish failed: " + error);
        }
    }

    static String packageNameOf(Object item) {
        if (item == null) return "";
        Object component = invoke(item, "getTargetComponent");
        if (!(component instanceof ComponentName)) {
            component = invoke(item, "getComponentName");
        }
        if (component instanceof ComponentName) {
            return ((ComponentName) component).getPackageName();
        }
        return "";
    }

    private static Object invoke(Object target, String method) {
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(target, method);
        return result.succeeded() ? result.value() : null;
    }
}
