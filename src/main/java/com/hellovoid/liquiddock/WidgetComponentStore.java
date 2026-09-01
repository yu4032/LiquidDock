package com.hellovoid.liquiddock;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared descriptor/selector codec plus the narrow Launcher -> module discovery channel. */
public final class WidgetComponentStore {
    public static final String MODULE_PACKAGE = "com.hellovoid.liquiddock";
    public static final String RECEIVER_CLASS = MODULE_PACKAGE + ".WidgetDiscoveryReceiver";
    public static final String ACTION_DISCOVER = MODULE_PACKAGE + ".WIDGET_COMPONENT_DISCOVERED";
    public static final String EXTRA_DESCRIPTOR = "descriptor"; // legacy single-item receive fallback
    public static final String EXTRA_DESCRIPTORS = "descriptors";
    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_REQUEST_ACK = "request_ack";
    public static final String CATALOG_PREFS = "widget_components";
    public static final String CATALOG_KEY = "catalog";
    public static final String DISCOVERY_TOKEN_KEY = "widget_discovery_token";
    public static final String DISCOVERY_REQUEST_KEY = "widget_discovery_request";
    public static final String SELECTION_KEY = "widget_hidden_components";

    private static final String REMOTE = "R"; // retired coarse selector, parse-only rejection marker
    public static final String REMOTE_V2 = "R2";
    private static final String MAML = "M";
    public static final String MAML_V2 = "M2";
    private static final String SEP = "\t";
    static final int BATCH_MAX_ITEMS = 128;

    public static final String ACTION_CLEAR_BACKGROUND = "background";
    public static final String ACTION_CLEAR_IMAGE = "image";
    public static final String ACTION_HIDE_VIEW = "hide";
    public static final String TYPE_BACKGROUND = "background";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_CONTAINER = "container";
    public static final String TYPE_INTERACTIVE = "interactive";
    public static final String TYPE_OTHER = "other";
    public static final String TYPE_INTERNAL = "internal";

    // A manual load always restarts Launcher. Cache that process's request/token so clearing the
    // persisted request does not interrupt the remainder of this one Launcher discovery session.
    private static volatile boolean discoverySessionLoaded;
    private static volatile boolean discoveryActive;
    private static volatile String discoveryToken = "";
    private static volatile boolean discoveryAckSent;

    private WidgetComponentStore() {}

    static boolean discoveryRequested() {
        ensureDiscoverySession();
        return discoveryActive;
    }

    static void acknowledgeDiscoveryRequest(Context context) {
        ensureDiscoverySession();
        if (!discoveryActive || context == null || discoveryAckSent || blank(discoveryToken)) return;
        synchronized (WidgetComponentStore.class) {
            if (discoveryAckSent) return;
            discoveryAckSent = true;
        }
        try {
            Intent intent = baseIntent();
            intent.putExtra(EXTRA_REQUEST_ACK, true);
            context.sendBroadcast(intent);
        } catch (Throwable error) {
            if (MainHook.debugLogging) MainHook.log("[DC][WidgetDiscover] ack failed: " + error);
        }
    }

    static Descriptor remoteDescriptor(
            String provider,
            String action,
            String resourceName,
            String className,
            String hierarchyPath,
            String componentType) {
        if (blank(provider) || blank(action) || blank(className)
                || blank(hierarchyPath) || blank(componentType) || unsafe(resourceName)) return null;
        return new Descriptor(
                REMOTE_V2, provider, action, safe(resourceName), className, "",
                hierarchyPath, componentType);
    }

    static Descriptor mamlDescriptor(
            WidgetBackgroundIdentity identity, String elementName, String className) {
        if (identity == null || blank(identity.productId)
                || blank(elementName) || blank(className)) return null;
        return new Descriptor(
                MAML, identity.productId, ACTION_HIDE_VIEW, elementName, className,
                identity.appPackage == null ? "" : identity.appPackage,
                "mElements/" + elementName, classifyMamlType(className));
    }

    /**
     * Exact render-tree descriptor for MAML elements that cannot be safely addressed through
     * ScreenElementRoot.findElement(name), including anonymous and dontAddToMap elements.
     */
    static Descriptor mamlRenderDescriptor(
            WidgetBackgroundIdentity identity,
            String elementName,
            String className,
            String hierarchyPath) {
        if (identity == null || blank(identity.productId) || unsafe(elementName)
                || blank(className) || blank(hierarchyPath)) return null;
        return new Descriptor(
                MAML_V2, identity.productId, ACTION_HIDE_VIEW, safe(elementName), className,
                identity.appPackage == null ? "" : identity.appPackage,
                hierarchyPath, classifyMamlType(className));
    }

    /**
     * Sends descriptors in bounded chunks instead of one broadcast per node. Exact-path discovery
     * can produce hundreds of descriptors for a single calendar widget, so per-node IPC is both
     * expensive and prone to partial catalog delivery under a restart burst.
     */
    static void publishBatch(Context context, List<Descriptor> descriptors) {
        ensureDiscoverySession();
        if (!discoveryActive || context == null || blank(discoveryToken)
                || descriptors == null || descriptors.isEmpty()) return;

        ArrayList<String> batch = new ArrayList<>(BATCH_MAX_ITEMS);
        for (Descriptor descriptor : descriptors) {
            if (descriptor == null) continue;
            batch.add(descriptor.encodeCatalog());
            if (batch.size() >= BATCH_MAX_ITEMS) {
                sendBatch(context, batch);
                batch = new ArrayList<>(BATCH_MAX_ITEMS);
            }
        }
        if (!batch.isEmpty()) sendBatch(context, batch);
    }

    private static void sendBatch(Context context, ArrayList<String> batch) {
        try {
            Intent intent = baseIntent();
            intent.putStringArrayListExtra(EXTRA_DESCRIPTORS, batch);
            context.sendBroadcast(intent);
        } catch (Throwable error) {
            if (MainHook.debugLogging) MainHook.log("[DC][WidgetDiscover] batch publish failed: " + error);
        }
    }

    private static Intent baseIntent() {
        Intent intent = new Intent(ACTION_DISCOVER);
        intent.setComponent(new ComponentName(MODULE_PACKAGE, RECEIVER_CLASS));
        intent.putExtra(EXTRA_TOKEN, discoveryToken);
        return intent;
    }

    private static void ensureDiscoverySession() {
        if (discoverySessionLoaded) return;
        synchronized (WidgetComponentStore.class) {
            if (discoverySessionLoaded) return;
            try {
                ConfigReader config = ConfigReader.load();
                discoveryToken = config.s(DISCOVERY_TOKEN_KEY, "");
                discoveryActive = !blank(config.s(DISCOVERY_REQUEST_KEY, ""));
            } catch (Throwable ignored) {
                discoveryToken = "";
                discoveryActive = false;
            }
            discoverySessionLoaded = true;
        }
    }

    public static Descriptor parseCatalog(String encoded) {
        if (encoded == null) return null;
        String[] parts = encoded.split(SEP, -1);
        if ((parts.length == 8 || parts.length == 12) && REMOTE_V2.equals(parts[0])) {
            if (blank(parts[1]) || blank(parts[2]) || unsafe(parts[3]) || blank(parts[4])
                    || unsafe(parts[5]) || unsafe(parts[6]) || blank(parts[7])) return null;
            Descriptor descriptor = new Descriptor(parts[0], parts[1], parts[2], parts[3], parts[4],
                    parts[6], parts[5], parts[7]);
            return parts.length == 12 ? descriptor.withDiscoveryMetadata(
                    parseInt(parts[8]), parseInt(parts[9]), parseFloat(parts[10]), parseFloat(parts[11]))
                    : descriptor;
        }
        if ((parts.length == 5 || parts.length == 9) && MAML.equals(parts[0])) {
            if (blank(parts[1]) || blank(parts[2]) || blank(parts[3]) || unsafe(parts[4])) return null;
            Descriptor descriptor = new Descriptor(MAML, parts[1], ACTION_HIDE_VIEW, parts[2], parts[3],
                    parts[4], "mElements/" + parts[2], classifyMamlType(parts[3]));
            return parts.length == 9 ? descriptor.withDiscoveryMetadata(
                    parseInt(parts[5]), parseInt(parts[6]), parseFloat(parts[7]), parseFloat(parts[8]))
                    : descriptor;
        }
        if ((parts.length == 7 || parts.length == 11) && MAML_V2.equals(parts[0])) {
            if (blank(parts[1]) || unsafe(parts[2]) || blank(parts[3]) || blank(parts[4])
                    || unsafe(parts[5]) || blank(parts[6])) return null;
            Descriptor descriptor = new Descriptor(MAML_V2, parts[1], ACTION_HIDE_VIEW, parts[2], parts[3],
                    parts[5], parts[4], parts[6]);
            return parts.length == 11 ? descriptor.withDiscoveryMetadata(
                    parseInt(parts[7]), parseInt(parts[8]), parseFloat(parts[9]), parseFloat(parts[10]))
                    : descriptor;
        }
        return null;
    }

    public static Descriptor parseSelector(String encoded) {
        if (encoded == null) return null;
        String[] parts = encoded.split(SEP, -1);
        if (parts.length > 0 && REMOTE.equals(parts[0])) return null;
        if (parts.length == 6 && REMOTE_V2.equals(parts[0])) {
            if (blank(parts[1]) || blank(parts[2]) || unsafe(parts[3]) || blank(parts[4])
                    || blank(parts[5])) return null;
            return new Descriptor(REMOTE_V2, parts[1], parts[2], parts[3], parts[4], "",
                    parts[5], typeForAction(parts[2]));
        }
        if (parts.length == 4 && MAML.equals(parts[0])) {
            if (blank(parts[1]) || blank(parts[2]) || blank(parts[3])) return null;
            return new Descriptor(MAML, parts[1], ACTION_HIDE_VIEW, parts[2], parts[3], "",
                    "mElements/" + parts[2], classifyMamlType(parts[3]));
        }
        if (parts.length == 5 && MAML_V2.equals(parts[0])) {
            if (blank(parts[1]) || unsafe(parts[2]) || blank(parts[3]) || blank(parts[4])) return null;
            return new Descriptor(MAML_V2, parts[1], ACTION_HIDE_VIEW, parts[2], parts[3], "",
                    parts[4], classifyMamlType(parts[3]));
        }
        return null;
    }

    private static String typeForAction(String action) {
        if (ACTION_CLEAR_BACKGROUND.equals(action)) return TYPE_BACKGROUND;
        if (ACTION_CLEAR_IMAGE.equals(action)) return TYPE_IMAGE;
        return TYPE_OTHER;
    }

    private static String classifyMamlType(String className) {
        String simple = className == null ? "" : className.substring(className.lastIndexOf('.') + 1);
        if (simple.contains("VariableElement")) return TYPE_INTERNAL;
        if (simple.contains("Image")) return TYPE_IMAGE;
        if (simple.contains("Text") || simple.contains("DateTime")) return TYPE_TEXT;
        if (simple.contains("Rectangle") || simple.contains("Circle") || simple.contains("Arc")) {
            return TYPE_BACKGROUND;
        }
        if (simple.contains("Group")) return TYPE_CONTAINER;
        return TYPE_OTHER;
    }

    private static int parseInt(String value) {
        try { return Integer.parseInt(value); }
        catch (Throwable ignored) { return -1; }
    }

    private static float parseFloat(String value) {
        if (value == null || value.isEmpty()) return Float.NaN;
        try {
            float parsed = Float.parseFloat(value);
            return Float.isInfinite(parsed) ? Float.NaN : parsed;
        } catch (Throwable ignored) {
            return Float.NaN;
        }
    }

    private static String encodeFloat(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? "" : Float.toString(value);
    }

    private static boolean unsafe(String value) {
        return value == null || value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
    }

    private static boolean blank(String value) {
        return value == null || value.isEmpty() || unsafe(value);
    }

    private static String safe(String value) { return value == null ? "" : value; }

    public static final class Descriptor {
        public final String source;
        public final String owner;
        public final String action;
        public final String name;
        public final String className;
        public final String label;
        public final String hierarchyPath;
        public final String componentType;
        public final int renderOrdinal;
        public final int depth;
        public final float areaRatio;
        public final float effectiveZ;

        Descriptor(
                String source, String owner, String action, String name, String className,
                String label, String hierarchyPath, String componentType) {
            this(source, owner, action, name, className, label, hierarchyPath, componentType,
                    -1, -1, Float.NaN, Float.NaN);
        }

        private Descriptor(
                String source, String owner, String action, String name, String className,
                String label, String hierarchyPath, String componentType,
                int renderOrdinal, int depth, float areaRatio, float effectiveZ) {
            this.source = source;
            this.owner = owner;
            this.action = action;
            this.name = name == null ? "" : name;
            this.className = className;
            this.label = label == null ? "" : label;
            this.hierarchyPath = hierarchyPath == null ? "" : hierarchyPath;
            this.componentType = componentType == null ? TYPE_OTHER : componentType;
            this.renderOrdinal = renderOrdinal < 0 ? -1 : renderOrdinal;
            this.depth = depth < 0 ? -1 : depth;
            this.areaRatio = Float.isNaN(areaRatio) || Float.isInfinite(areaRatio)
                    ? Float.NaN : Math.max(0f, Math.min(1f, areaRatio));
            this.effectiveZ = Float.isNaN(effectiveZ) || Float.isInfinite(effectiveZ)
                    ? Float.NaN : effectiveZ;
        }

        public Descriptor withDiscoveryMetadata(
                int renderOrdinal, int depth, float areaRatio, float effectiveZ) {
            return new Descriptor(source, owner, action, name, className, label,
                    hierarchyPath, componentType, renderOrdinal, depth, areaRatio, effectiveZ);
        }

        public boolean hasDiscoveryMetadata() {
            return renderOrdinal >= 0 && depth >= 0;
        }

        public boolean isRemoteViews() { return REMOTE_V2.equals(source); }
        public boolean isMaml() { return MAML.equals(source) || MAML_V2.equals(source); }
        public boolean isExactMamlRender() { return MAML_V2.equals(source); }

        public String selectorKey() {
            if (isRemoteViews()) {
                return REMOTE_V2 + SEP + owner + SEP + action + SEP + name + SEP
                        + className + SEP + hierarchyPath;
            }
            if (isExactMamlRender()) {
                return MAML_V2 + SEP + owner + SEP + name + SEP + className + SEP + hierarchyPath;
            }
            return MAML + SEP + owner + SEP + name + SEP + className;
        }

        public String encodeCatalog() {
            String base;
            if (isRemoteViews()) {
                base = selectorKey() + SEP + label + SEP + componentType;
            } else if (isExactMamlRender()) {
                base = selectorKey() + SEP + label + SEP + componentType;
            } else {
                base = selectorKey() + SEP + label;
            }
            if (!hasDiscoveryMetadata()) return base;
            return base + SEP + renderOrdinal + SEP + depth + SEP
                    + encodeFloat(areaRatio) + SEP + encodeFloat(effectiveZ);
        }

        public String displayOwner() {
            return label.isEmpty() ? owner : label + " · " + owner;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Descriptor)) return false;
            Descriptor that = (Descriptor) other;
            return source.equals(that.source) && owner.equals(that.owner)
                    && action.equals(that.action) && name.equals(that.name)
                    && className.equals(that.className) && label.equals(that.label)
                    && hierarchyPath.equals(that.hierarchyPath)
                    && componentType.equals(that.componentType)
                    && renderOrdinal == that.renderOrdinal && depth == that.depth
                    && Float.compare(areaRatio, that.areaRatio) == 0
                    && Float.compare(effectiveZ, that.effectiveZ) == 0;
        }

        @Override public int hashCode() {
            return Objects.hash(source, owner, action, name, className, label,
                    hierarchyPath, componentType, renderOrdinal, depth, areaRatio, effectiveZ);
        }
    }
}
