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

    private static final String REMOTE = "R";
    private static final String MAML = "M";
    private static final String SEP = "\t";

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
            Context context, String provider, String resourceName, String className) {
        if (context == null || blank(provider) || blank(resourceName) || blank(className)) return;
        publish(context, new Descriptor(REMOTE, provider, resourceName, className, ""));
    }

    static void publishMaml(
            Context context, WidgetBackgroundIdentity identity, String elementName, String className) {
        if (context == null || identity == null || blank(identity.productId)
                || blank(elementName) || blank(className)) return;
        publish(context, new Descriptor(
                MAML, identity.productId, elementName, className,
                identity.appPackage == null ? "" : identity.appPackage));
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
        if (parts.length != 5) return null;
        if (!REMOTE.equals(parts[0]) && !MAML.equals(parts[0])) return null;
        if (blank(parts[1]) || blank(parts[2]) || blank(parts[3])) return null;
        return new Descriptor(parts[0], parts[1], parts[2], parts[3], parts[4]);
    }

    public static Descriptor parseSelector(String encoded) {
        if (encoded == null) return null;
        String[] parts = encoded.split(SEP, -1);
        if (parts.length != 4) return null;
        if (!REMOTE.equals(parts[0]) && !MAML.equals(parts[0])) return null;
        if (blank(parts[1]) || blank(parts[2]) || blank(parts[3])) return null;
        return new Descriptor(parts[0], parts[1], parts[2], parts[3], "");
    }

    private static boolean blank(String value) {
        return value == null || value.isEmpty() || value.indexOf('\t') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }

    public static final class Descriptor {
        public final String source;
        public final String owner;
        public final String name;
        public final String className;
        public final String label;

        Descriptor(String source, String owner, String name, String className, String label) {
            this.source = source;
            this.owner = owner;
            this.name = name;
            this.className = className;
            this.label = label == null ? "" : label;
        }

        public boolean isRemoteViews() { return REMOTE.equals(source); }
        public boolean isMaml() { return MAML.equals(source); }

        public String selectorKey() {
            return source + SEP + owner + SEP + name + SEP + className;
        }

        public String encodeCatalog() {
            return selectorKey() + SEP + label;
        }

        public String displayOwner() {
            return label.isEmpty() ? owner : label + " · " + owner;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Descriptor)) return false;
            Descriptor that = (Descriptor) other;
            return source.equals(that.source) && owner.equals(that.owner)
                    && name.equals(that.name) && className.equals(that.className)
                    && label.equals(that.label);
        }

        @Override public int hashCode() {
            return Objects.hash(source, owner, name, className, label);
        }
    }
}
