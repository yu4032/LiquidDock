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
import java.util.IdentityHashMap;
import java.util.List;
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
        int[] renderOrdinal = {0};
        float rootArea = viewArea(content);
        if (!(rootArea > 0f)) rootArea = viewArea(host);
        // Root path 0 may itself own the visual background. It is selectable for property-level
        // background/image actions, but never for the destructive whole-node hide action.
        scanNode(content, provider, "0", false, new HashSet<>(), descriptors,
                renderOrdinal, 0, rootArea);

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

        ArrayList<WidgetComponentStore.Descriptor> descriptors = new ArrayList<>();
        Map<Object, DiscoveryMetadata> renderMetadata = new IdentityHashMap<>();
        float rootArea = viewArea(host);

        // Launcher 4.50 renders ScreenElementRoot.mInnerGroup, whose ElementGroup.mElements list
        // contains every XML child. ScreenElementRoot.mElements is only a name index. Walk the real
        // render tree first so both named and anonymous elements can share the same render ordinal.
        Object innerGroup = readField(root, "mInnerGroup");
        if (innerGroup != null) {
            Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            int[] renderOrdinal = {0};
            scanMamlRenderChildren(root, host, identity, innerGroup, "render", 0,
                    rootArea, renderOrdinal, visited, renderMetadata, descriptors);
        }

        // Preserve the legacy, stable name-based selector for every element that Launcher exposes
        // through ScreenElementRoot.findElement(name). Existing user selections remain valid while
        // discovery metadata is copied from that same element's real render-tree position.
        Object value = readField(root, "mElements");
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                String name = String.valueOf(entry.getKey());
                Object stored = entry.getValue();
                Object element = stored instanceof WeakReference
                        ? ((WeakReference<?>) stored).get() : stored;
                if (element == null) continue;
                String className = element.getClass().getName();
                DiscoveryMetadata metadata = renderMetadata.get(element);
                MainHook.log(TAG
                        + " source=maml"
                        + " provider=" + safe(identity.appPackage)
                        + " productId=" + safe(identity.productId)
                        + " name=" + name
                        + " class=" + className
                        + " hierarchyPath=mElements/" + name
                        + metadataLog(metadata));
                WidgetComponentStore.Descriptor descriptor =
                        WidgetComponentStore.mamlDescriptor(identity, name, className);
                if (descriptor != null && metadata != null) {
                    descriptor = metadata.enrich(descriptor);
                }
                if (descriptor != null) descriptors.add(descriptor);
            }
        }

        WidgetComponentStore.publishBatch(host.getContext(), descriptors);
        WidgetComponentStore.acknowledgeDiscoveryRequest(host.getContext());
    }

    private static void scanMamlRenderChildren(
            Object root,
            View host,
            WidgetBackgroundIdentity identity,
            Object group,
            String parentPath,
            int depth,
            float rootArea,
            int[] renderOrdinal,
            Set<Object> visited,
            Map<Object, DiscoveryMetadata> renderMetadata,
            ArrayList<WidgetComponentStore.Descriptor> descriptors) {
        if (group == null || !visited.add(group)) return;
        Object childrenValue = readField(group, "mElements");
        if (!(childrenValue instanceof List)) return;
        List<?> children = (List<?>) childrenValue;
        for (int i = 0; i < children.size(); i++) {
            Object element = children.get(i);
            if (element == null) continue;
            String hierarchyPath = parentPath + "/" + i;
            String name = readStringField(element, "mName");
            String className = element.getClass().getName();
            DiscoveryMetadata metadata = new DiscoveryMetadata(
                    renderOrdinal[0]++, depth, mamlAreaRatio(element, rootArea), Float.NaN);
            renderMetadata.put(element, metadata);

            HookUtil.InvocationResult<Object> namedTargetResult = name.isEmpty()
                    ? null : HookUtil.tryInvoke(root, "findElement", name);
            Object namedTarget = namedTargetResult != null && namedTargetResult.succeeded()
                    ? namedTargetResult.value() : null;
            if (namedTarget != element) {
                MainHook.log(TAG
                        + " source=maml-render"
                        + " provider=" + safe(identity.appPackage)
                        + " productId=" + safe(identity.productId)
                        + " name=" + safe(name)
                        + " class=" + className
                        + " hierarchyPath=" + hierarchyPath
                        + metadataLog(metadata));
                WidgetComponentStore.Descriptor descriptor =
                        WidgetComponentStore.mamlRenderDescriptor(
                                identity, name, className, hierarchyPath);
                if (descriptor != null) descriptors.add(metadata.enrich(descriptor));
            }

            // ElementGroup subclasses expose their actual children through mElements. Other
            // ScreenElements simply stop here because the reflected field is unavailable.
            scanMamlRenderChildren(root, host, identity, element, hierarchyPath, depth + 1,
                    rootArea, renderOrdinal, visited, renderMetadata, descriptors);
        }
    }

    private static void scanNode(
            View view, String provider, String hierarchyPath, boolean allowWholeNodeHide,
            Set<String> published, ArrayList<WidgetComponentStore.Descriptor> descriptors,
            int[] renderOrdinal, int depth, float rootArea) {
        if (view == null) return;
        String resourceName = resourceEntryName(view);
        String className = view.getClass().getName();
        boolean hasBackground = view.getBackground() != null;
        boolean hasImage = view instanceof ImageView && ((ImageView) view).getDrawable() != null;
        DiscoveryMetadata metadata = new DiscoveryMetadata(
                renderOrdinal[0]++, depth, areaRatio(viewArea(view), rootArea), safeViewZ(view));
        MainHook.log(TAG
                + " source=remoteviews"
                + " provider=" + safe(provider)
                + " class=" + className
                + " resource=" + resourceName
                + " hierarchyPath=" + hierarchyPath
                + " hasBackground=" + hasBackground
                + " hasImage=" + hasImage
                + metadataLog(metadata));

        if (hasBackground) {
            collectRemoteAction(provider, WidgetComponentStore.ACTION_CLEAR_BACKGROUND,
                    resourceName, className, hierarchyPath, WidgetComponentStore.TYPE_BACKGROUND,
                    metadata, published, descriptors);
        }
        if (hasImage) {
            collectRemoteAction(provider, WidgetComponentStore.ACTION_CLEAR_IMAGE,
                    resourceName, className, hierarchyPath, WidgetComponentStore.TYPE_IMAGE,
                    metadata, published, descriptors);
        }
        if (allowWholeNodeHide) {
            collectRemoteAction(provider, WidgetComponentStore.ACTION_HIDE_VIEW,
                    resourceName, className, hierarchyPath, classifyWholeNode(view),
                    metadata, published, descriptors);
        }

        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child != null) {
                scanNode(child, provider, hierarchyPath + "/" + i, true, published, descriptors,
                        renderOrdinal, depth + 1, rootArea);
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
            DiscoveryMetadata metadata,
            Set<String> published,
            ArrayList<WidgetComponentStore.Descriptor> descriptors) {
        String key = action + '\t' + hierarchyPath + '\t' + className + '\t' + resourceName;
        if (!published.add(key)) return;
        WidgetComponentStore.Descriptor descriptor = WidgetComponentStore.remoteDescriptor(
                provider, action, resourceName, className, hierarchyPath, componentType);
        if (descriptor != null) descriptors.add(metadata.enrich(descriptor));
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

    private static float viewArea(View view) {
        if (view == null) return Float.NaN;
        float width = view.getWidth();
        float height = view.getHeight();
        if (!(width > 0f) || !(height > 0f)) {
            width = view.getMeasuredWidth();
            height = view.getMeasuredHeight();
        }
        return width > 0f && height > 0f ? width * height : Float.NaN;
    }

    private static float areaRatio(float area, float rootArea) {
        if (Float.isNaN(area) || Float.isNaN(rootArea) || !(rootArea > 0f)) return Float.NaN;
        return Math.max(0f, Math.min(1f, area / rootArea));
    }

    private static float mamlAreaRatio(Object element, float rootArea) {
        Number width = invokeNumber(element, "getWidth");
        Number height = invokeNumber(element, "getHeight");
        if (width == null || height == null) return Float.NaN;
        float w = width.floatValue();
        float h = height.floatValue();
        return w > 0f && h > 0f ? areaRatio(w * h, rootArea) : Float.NaN;
    }

    private static Number invokeNumber(Object target, String methodName) {
        if (target == null) return null;
        HookUtil.InvocationResult<Object> valueResult = HookUtil.tryInvoke(target, methodName);
        if (!valueResult.succeeded()) return null;
        Object value = valueResult.value();
        return value instanceof Number ? (Number) value : null;
    }

    private static float safeViewZ(View view) {
        try { return view.getZ(); }
        catch (Throwable ignored) { return Float.NaN; }
    }

    private static Object readField(Object target, String name) {
        if (target == null) return null;
        try { return HookUtil.getField(target, name); }
        catch (Throwable ignored) { return null; }
    }

    private static String readStringField(Object target, String name) {
        Object value = readField(target, name);
        return value instanceof String ? (String) value : "";
    }

    private static String metadataLog(DiscoveryMetadata metadata) {
        if (metadata == null) return "";
        return " renderOrdinal=" + metadata.renderOrdinal
                + " depth=" + metadata.depth
                + " areaRatio=" + metadata.areaRatio
                + " z=" + metadata.effectiveZ;
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace(' ', '_');
    }

    private static final class DiscoveryMetadata {
        final int renderOrdinal;
        final int depth;
        final float areaRatio;
        final float effectiveZ;

        DiscoveryMetadata(int renderOrdinal, int depth, float areaRatio, float effectiveZ) {
            this.renderOrdinal = renderOrdinal;
            this.depth = depth;
            this.areaRatio = areaRatio;
            this.effectiveZ = effectiveZ;
        }

        WidgetComponentStore.Descriptor enrich(WidgetComponentStore.Descriptor descriptor) {
            return descriptor.withDiscoveryMetadata(renderOrdinal, depth, areaRatio, effectiveZ);
        }
    }
}
