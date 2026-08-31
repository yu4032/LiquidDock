package com.hellovoid.liquiddock;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Read-only runtime discovery feeding the widget-component picker catalog. */
final class LauncherWidgetComponentDiscovery {
    private static final String TAG = "[DC][WidgetDiscover]";
    private static final Map<View, Integer> DUMPED_REMOTE_ROOTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> DUMPED_MAML_ROOTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LauncherWidgetComponentDiscovery() {}

    static void scan(View host) {
        if (!WidgetComponentStore.discoveryRequested()) return;
        if (host == null || isMamlHost(host)) return;
        View content = resolveRemoteViewsContent(host);
        if (content == null) return;

        // Launcher can expose a RemoteViews content root before a provider has finished populating
        // its descendants. Calendar does this during restore/update. A permanent "seen root" bit
        // would lock in that empty/partial tree. Use a shallow structural snapshot instead: normal
        // re-bind attempts with the same shape are cheap skips, while the same root becoming richer
        // after updateAppWidget is allowed one new full discovery pass.
        int snapshotSignature = remoteSnapshotSignature(content);
        Integer previousSignature;
        synchronized (DUMPED_REMOTE_ROOTS) {
            previousSignature = DUMPED_REMOTE_ROOTS.get(content);
            if (previousSignature != null && previousSignature == snapshotSignature) return;
        }

        String provider = providerIdentity(host);
        ArrayList<WidgetComponentStore.Descriptor> descriptors = new ArrayList<>();
        // Root path 0 may itself own the visual background. It is selectable for property-level
        // background/image actions, but never for the destructive whole-node hide action.
        scanNode(content, provider, "0", false, new HashSet<>(), descriptors);

        if (!descriptors.isEmpty()) {
            WidgetComponentStore.publishBatch(host.getContext(), descriptors);
            synchronized (DUMPED_REMOTE_ROOTS) {
                DUMPED_REMOTE_ROOTS.put(content, snapshotSignature);
            }
        }
        // ACK even when this early snapshot is empty. The persisted one-shot request must still be
        // cleared, while this Launcher process keeps its in-memory discovery session active so a
        // later provider update can retry the same root when its snapshot changes.
        WidgetComponentStore.acknowledgeDiscoveryRequest(host.getContext());
    }

    static void scanMaml(View host, WidgetBackgroundIdentity identity, Object root) {
        if (!WidgetComponentStore.discoveryRequested()) return;
        if (host == null || identity == null || root == null) return;
        synchronized (DUMPED_MAML_ROOTS) {
            if (DUMPED_MAML_ROOTS.containsKey(root)) return;
            DUMPED_MAML_ROOTS.put(root, Boolean.TRUE);
        }
        Object value = readField(root, "mElements");
        if (!(value instanceof Map)) return;
        ArrayList<WidgetComponentStore.Descriptor> descriptors = new ArrayList<>();
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
            WidgetComponentStore.Descriptor descriptor =
                    WidgetComponentStore.mamlDescriptor(identity, name, className);
            if (descriptor != null) descriptors.add(descriptor);
        }
        WidgetComponentStore.publishBatch(host.getContext(), descriptors);
        WidgetComponentStore.acknowledgeDiscoveryRequest(host.getContext());
    }

    private static void scanNode(
            View view, String provider, String hierarchyPath, boolean allowWholeNodeHide,
            Set<String> published, ArrayList<WidgetComponentStore.Descriptor> descriptors) {
        if (view == null) return;
        String resourceName = resourceEntryName(view);
        String className = view.getClass().getName();
        boolean hasBackground = view.getBackground() != null;
        boolean hasImage = view instanceof ImageView && ((ImageView) view).getDrawable() != null;
        MainHook.log(TAG
                + " source=remoteviews"
                + " provider=" + safe(provider)
                + " class=" + className
                + " resource=" + resourceName
                + " hierarchyPath=" + hierarchyPath
                + " hasBackground=" + hasBackground
                + " hasImage=" + hasImage);

        if (hasBackground) {
            collectRemoteAction(provider, WidgetComponentStore.ACTION_CLEAR_BACKGROUND,
                    resourceName, className, hierarchyPath, WidgetComponentStore.TYPE_BACKGROUND,
                    published, descriptors);
        }
        if (hasImage) {
            collectRemoteAction(provider, WidgetComponentStore.ACTION_CLEAR_IMAGE,
                    resourceName, className, hierarchyPath, WidgetComponentStore.TYPE_IMAGE,
                    published, descriptors);
        }
        if (allowWholeNodeHide) {
            collectRemoteAction(provider, WidgetComponentStore.ACTION_HIDE_VIEW,
                    resourceName, className, hierarchyPath, classifyWholeNode(view),
                    published, descriptors);
        }

        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child != null) {
                scanNode(child, provider, hierarchyPath + "/" + i, true, published, descriptors);
            }
        }
    }

    private static void collectRemoteAction(
            String provider,
            String action,
            String resourceName,
            String className,
            String hierarchyPath,
            String componentType,
            Set<String> published,
            ArrayList<WidgetComponentStore.Descriptor> descriptors) {
        String key = action + '\t' + hierarchyPath + '\t' + className + '\t' + resourceName;
        if (!published.add(key)) return;
        WidgetComponentStore.Descriptor descriptor = WidgetComponentStore.remoteDescriptor(
                provider, action, resourceName, className, hierarchyPath, componentType);
        if (descriptor != null) descriptors.add(descriptor);
    }

    /**
     * Cheap provider-update signal without walking a potentially huge Calendar tree on every bind.
     * Depth 2 includes the root, its direct children, and grandchildren plus each group's child
     * count, which captures Calendar's background -> widget_frame -> main_container population.
     */
    private static int remoteSnapshotSignature(View content) {
        return appendRemoteSnapshot(content, 2, 17);
    }

    private static int appendRemoteSnapshot(View view, int depth, int signature) {
        if (view == null) return signature * 31;
        int result = signature;
        result = 31 * result + view.getClass().getName().hashCode();
        result = 31 * result + view.getId();
        if (!(view instanceof ViewGroup)) return result;
        ViewGroup group = (ViewGroup) view;
        int childCount = group.getChildCount();
        result = 31 * result + childCount;
        if (depth <= 0) return result;
        for (int i = 0; i < childCount; i++) {
            result = appendRemoteSnapshot(group.getChildAt(i), depth - 1, result);
        }
        return result;
    }

    private static String classifyWholeNode(View view) {
        if (view instanceof TextView) return WidgetComponentStore.TYPE_TEXT;
        if (view instanceof ViewGroup) return WidgetComponentStore.TYPE_CONTAINER;
        if (view.isClickable() || view.isLongClickable()) return WidgetComponentStore.TYPE_INTERACTIVE;
        return WidgetComponentStore.TYPE_OTHER;
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
