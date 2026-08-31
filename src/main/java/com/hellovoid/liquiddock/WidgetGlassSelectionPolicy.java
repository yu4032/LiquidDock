package com.hellovoid.liquiddock;

import java.util.Set;

/** Pure type-level opt-in policy for Launcher widget glass. */
public final class WidgetGlassSelectionPolicy {
    public static final String SELECTION_KEY = "widget_glass_groups";
    private static final String MAML_GROUP = "M";
    private static final String SEP = "\t";

    private WidgetGlassSelectionPolicy() {}

    public static boolean isEnabled(Set<String> selected, String groupKey) {
        return selected != null && groupKey != null && !groupKey.isEmpty()
                && selected.contains(groupKey);
    }

    public static String groupKey(WidgetComponentStore.Descriptor descriptor) {
        if (descriptor == null || descriptor.owner == null || descriptor.owner.isEmpty()) return "";
        return (descriptor.isMaml() ? MAML_GROUP : descriptor.source) + SEP + descriptor.owner;
    }

    static String remoteGroupKey(String provider) {
        if (provider == null || provider.isEmpty()) return "";
        return WidgetComponentStore.REMOTE_V2 + SEP + provider;
    }

    static String mamlGroupKey(String productId) {
        if (productId == null || productId.isEmpty()) return "";
        return MAML_GROUP + SEP + productId;
    }
}
