package com.hellovoid.liquiddock;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Read-only runtime discovery for the future widget-component picker UI.
 *
 * This spike deliberately does not hide, recolor, resize, or otherwise mutate provider content.
 * It only publishes stable-ish descriptors from the real Launcher widget trees so device testing
 * can tell us which selector fields are actually available on HyperOS.
 */
final class LauncherWidgetComponentDiscovery {
    private static final String TAG = "[DC][WidgetDiscover]";
    private static final Map<View, Boolean> DUMPED_REMOTE_ROOTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LauncherWidgetComponentDiscovery() {}

    static void scan(View host) {
        if (host == null || isMamlHost(host)) return;
        View content = resolveRemoteViewsContent(host);
        if (content == null) return;
        synchronized (DUMPED_REMOTE_ROOTS) {
            if (DUMPED_REMOTE_ROOTS.containsKey(content)) return;
            DUMPED_REMOTE_ROOTS.put(content, Boolean.TRUE);
        }
        String provider = providerIdentity(host);
        scanNode(content, provider, "0");
    }

    static void publishMaml(
            WidgetBackgroundIdentity identity, String elementName, String className) {
        if (elementName == null || elementName.isEmpty()) return;
        MainHook.log(TAG
                + " source=maml"
                + " provider=" + safe(identity != null ? identity.appPackage : null)
                + " productId=" + safe(identity != null ? identity.productId : null)
                + " name=" + elementName
                + " class=" + safe(className)
                + " hierarchyPath=mElements/" + elementName);
    }

    private static void scanNode(View view, String provider, String hierarchyPath) {
        if (view == null) return;
        MainHook.log(TAG
                + " source=remoteviews"
                + " provider=" + safe(provider)
                + " class=" + view.getClass().getName()
                + " resource=" + resourceEntryName(view)
                + " hierarchyPath=" + hierarchyPath);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child != null) scanNode(child, provider, hierarchyPath + "/" + i);
        }
    }

    private static View resolveRemoteViewsContent(View host) {
        if (!(host instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) host;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child != null && child.getTag(android.R.id.widget_frame) instanceof Integer) {
                return child;
            }
        }
        return null;
    }

    private static String providerIdentity(View host) {
        if (!(host instanceof AppWidgetHostView)) return host.getClass().getName();
        try {
            AppWidgetProviderInfo info = ((AppWidgetHostView) host).getAppWidgetInfo();
            if (info != null && info.provider != null) return info.provider.flattenToShortString();
        } catch (Throwable ignored) {}
        return host.getClass().getName();
    }

    private static String resourceEntryName(View view) {
        int id = view.getId();
        if (id == View.NO_ID) return "";
        try {
            return view.getResources().getResourceEntryName(id);
        } catch (Resources.NotFoundException ignored) {
            return "0x" + Integer.toHexString(id);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean isMamlHost(View host) {
        String name = host.getClass().getName();
        return name.endsWith(".MaMlHostView") || name.contains(".maml.");
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace(' ', '_');
    }
}
