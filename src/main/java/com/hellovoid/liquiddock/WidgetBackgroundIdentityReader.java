package com.hellovoid.liquiddock;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.view.View;

/** Reads only stable Launcher/AppWidget identity fields used by user-selected background rules. */
final class WidgetBackgroundIdentityReader {
    private WidgetBackgroundIdentityReader() {}

    static WidgetBackgroundIdentity maml(View host) {
        Object itemInfo = itemInfo(host);
        return new WidgetBackgroundIdentity(
                "maml",
                stringField(itemInfo, "productId"),
                stringField(itemInfo, "appPackage"),
                intField(itemInfo, "spanX", -1),
                intField(itemInfo, "spanY", -1),
                intField(itemInfo, "configSpanX", -1),
                intField(itemInfo, "configSpanY", -1));
    }

    static WidgetBackgroundIdentity remoteViews(View host) {
        Object itemInfo = itemInfo(host);
        String appPackage = stringField(itemInfo, "appPackage");
        if (appPackage == null && host instanceof AppWidgetHostView) {
            try {
                AppWidgetProviderInfo info = ((AppWidgetHostView) host).getAppWidgetInfo();
                ComponentName provider = info != null ? info.provider : null;
                if (provider != null) appPackage = provider.getPackageName();
            } catch (Throwable ignored) {}
        }
        return new WidgetBackgroundIdentity(
                "remoteviews",
                null,
                appPackage,
                intField(itemInfo, "spanX", -1),
                intField(itemInfo, "spanY", -1),
                intField(itemInfo, "configSpanX", intField(itemInfo, "spanX", -1)),
                intField(itemInfo, "configSpanY", intField(itemInfo, "spanY", -1)));
    }

    private static Object itemInfo(View host) {
        if (host == null) return null;
        try { return HookUtil.invoke(host, "getItemInfo"); }
        catch (Throwable ignored) {}
        try { return host.getTag(); }
        catch (Throwable ignored) { return null; }
    }

    private static String stringField(Object target, String name) {
        if (target == null) return null;
        try {
            Object value = HookUtil.getField(target, name);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) { return null; }
    }

    private static int intField(Object target, String name, int fallback) {
        if (target == null) return fallback;
        try {
            Object value = HookUtil.getField(target, name);
            return value instanceof Number ? ((Number) value).intValue() : fallback;
        } catch (Throwable ignored) { return fallback; }
    }
}
