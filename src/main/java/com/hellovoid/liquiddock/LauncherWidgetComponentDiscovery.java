package com.hellovoid.liquiddock;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Read-only runtime discovery feeding the widget-component picker catalog. */
final class LauncherWidgetComponentDiscovery {
    private static final String TAG = "[DC][WidgetDiscover]";
    private static final Map<View, Boolean> DUMPED_REMOTE_ROOTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> DUMPED_MAML_ROOTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LauncherWidgetComponentDiscovery() {}

    static void scan(View host) {
        if (!WidgetComponentStore.discoveryRequested()) return;
        if (host == null || isMamlHost(host)) return;
        WidgetComponentStore.acknowledgeDiscoveryRequest(host.getContext());
        View content = resolveRemoteViewsContent(host);
        if (content == null) return;
        synchronized (DUMPED_REMOTE_ROOTS) {
            if (DUMPED_REMOTE_ROOTS.containsKey(content)) return;
            DUMPED_REMOTE_ROOTS.put(content, Boolean.TRUE);
        }
        String provider = providerIdentity(host);
        // The direct RemoteViews content root is diagnostic only. Never publish it as selectable:
        // hiding that node would suppress the entire provider widget instead of one component.
        scanNode(content, provider, "0", false, new HashSet<>());
    }

    static void scanMaml(View host, WidgetBackgroundIdentity identity, Object root) {
        if (!WidgetComponentStore.discoveryRequested()) return;
        if (host == null || identity == null || root == null) return;
        WidgetComponentStore.acknowledgeDiscoveryRequest(host.getContext());
        synchronized (DUMPED_MAML_ROOTS) {
            if (DUMPED_MAML_ROOTS.containsKey(root)) return;
            DUMPED_MAML_ROOTS.put(root, Boolean.TRUE);
        }
        Object value = readField(root, "mElements");
        if (!(value instanceof Map)) return;
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            String name = String.valueOf(entry.getKey());
            Object stored = entry.getValue();
            Object element = stored instanceof WeakReference
                    ? ((WeakReference<?>) stored).get() : stored;
            if (element == null) continue;
            String className = element.getClass().getName();
            MainHook.log(TAG
                    + " source=maml"
                    + " provider=" + safe(identity.appPackage)
                    + " productId=" + safe(identity.productId)
                    + " name=" + name
                    + " class=" + className
                    + " hierarchyPath=mElements/" + name);
            WidgetComponentStore.publishMaml(host.getContext(), identity, name, className);
        }
    }

    private static void scanNode(
            View view, String provider, String hierarchyPath, boolean selectable,
            Set<String> published) {
        if (view == null) return;
        String resourceName = resourceEntryName(view);
        String className = view.getClass().getName();
        MainHook.log(TAG
                + " source=remoteviews"
                + " provider=" + safe(provider)
                + " class=" + className
                + " resource=" + resourceName
                + " hierarchyPath=" + hierarchyPath);
        if (selectable && !resourceName.isEmpty()) {
            String publishKey = resourceName + '\t' + className;
            if (published.add(publishKey)) {
                WidgetComponentStore.publishRemoteViews(
                        view.getContext(), provider, resourceName, className);
            }
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child != null) {
                scanNode(child, provider, hierarchyPath + "/" + i, true, published);
            }
        }
    }

    static View resolveRemoteViewsContent(View host) {
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

    static String providerIdentity(View host) {
        if (!(host instanceof AppWidgetHostView)) return host.getClass().getName();
        try {
            AppWidgetProviderInfo info = ((AppWidgetHostView) host).getAppWidgetInfo();
            if (info != null && info.provider != null) return info.provider.flattenToShortString();
        } catch (Throwable ignored) {}
        return host.getClass().getName();
    }

    static String resourceEntryName(View view) {
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

    static boolean isMamlHost(View host) {
        String name = host.getClass().getName();
        return name.endsWith(".MaMlHostView") || name.contains(".maml.");
    }

    private static Object readField(Object target, String name) {
        if (target == null) return null;
        try { return HookUtil.getField(target, name); }
        catch (Throwable ignored) { return null; }
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace(' ', '_');
    }
}
