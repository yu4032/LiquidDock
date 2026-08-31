package com.hellovoid.liquiddock;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.util.Objects;

/** Shared descriptor/selector codec plus the narrow Launcher -> module discovery channel. */
public final class WidgetComponentStore {
    public static final String MODULE_PACKAGE = "com.hellovoid.liquiddock";
    public static final String RECEIVER_CLASS = MODULE_PACKAGE + ".WidgetDiscoveryReceiver";
    public static final String ACTION_DISCOVER = MODULE_PACKAGE + ".WIDGET_COMPONENT_DISCOVERED";
    public static final String EXTRA_DESCRIPTOR = "descriptor";
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
    private static final String SEP = "\t";

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

    // A manual load always restarts Launcher. Cache that process's request/token so the first ACK
    // can clear persistent state without interrupting the rest of this one discovery pass.
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

    static void publishRemoteViews(
            Context context,
            String provider,
            String action,
            String resourceName,
            String className,
            String hierarchyPath,
            String componentType) {
        if (context == null || blank(provider) || blank(action) || blank(className)
                || blank(hierarchyPath) || blank(componentType) || unsafe(resourceName)) return;
        publish(context, new Descriptor(
                REMOTE_V2, provider, action, safe(resourceName), className, "",
                hierarchyPath, componentType));
    }

    static void publishMaml(
            Context context, WidgetBackgroundIdentity identity, String elementName, String className) {
        if (context == null || identity == null || blank(identity.productId)
                || blank(elementName) || blank(className)) return;
        publish(context, new Descriptor(
                MAML, identity.productId, ACTION_HIDE_VIEW, elementName, className,
                identity.appPackage == null ? "" : identity.appPackage,
                "mElements/" + elementName, classifyMamlType(className)));
    }

    private static void publish(Context context, Descriptor descriptor) {
        ensureDiscoverySession();
        if (!discoveryActive || blank(discoveryToken)) return;
        try {
            Intent intent = baseIntent();
            intent.putExtra(EXTRA_DESCRIPTOR, descriptor.encodeCatalog());
            context.sendBroadcast(intent);
        } catch (Throwable error) {
            if (MainHook.debugLogging) MainHook.log("[DC][WidgetDiscover] publish failed: " + error);
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
        if (parts.length == 8 && REMOTE_V2.equals(parts[0])) {
            if (blank(parts[1]) || blank(parts[2]) || unsafe(parts[3]) || blank(parts[4])
                    || unsafe(parts[5]) || blank(parts[6]) || blank(parts[7])) return null;
            return new Descriptor(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5],
                    parts[6], parts[7]);
        }
        if (parts.length == 5 && MAML.equals(parts[0])) {
            if (blank(parts[1]) || blank(parts[2]) || blank(parts[3]) || unsafe(parts[4])) return null;
            return new Descriptor(MAML, parts[1], ACTION_HIDE_VIEW, parts[2], parts[3], parts[4],
                    "mElements/" + parts[2], classifyMamlType(parts[3]));
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

        Descriptor(
                String source, String owner, String action, String name, String className,
                String label, String hierarchyPath, String componentType) {
            this.source = source;
            this.owner = owner;
            this.action = action;
            this.name = name == null ? "" : name;
            this.className = className;
            this.label = label == null ? "" : label;
            this.hierarchyPath = hierarchyPath == null ? "" : hierarchyPath;
            this.componentType = componentType == null ? TYPE_OTHER : componentType;
        }

        public boolean isRemoteViews() { return REMOTE_V2.equals(source); }
        public boolean isMaml() { return MAML.equals(source); }

        public String selectorKey() {
            if (isRemoteViews()) {
                return REMOTE_V2 + SEP + owner + SEP + action + SEP + name + SEP
                        + className + SEP + hierarchyPath;
            }
            return MAML + SEP + owner + SEP + name + SEP + className;
        }

        public String encodeCatalog() {
            if (isRemoteViews()) {
                return selectorKey() + SEP + label + SEP + componentType;
            }
            return selectorKey() + SEP + label;
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
                    && componentType.equals(that.componentType);
        }

        @Override public int hashCode() {
            return Objects.hash(source, owner, action, name, className, label,
                    hierarchyPath, componentType);
        }
    }
}
