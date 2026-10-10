package com.hellovoid.liquiddock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Whitelist only exact RemoteViews foreground parts.
 * A color change on MAML's enclosing View does not affect script-drawn elements.
 * Never whiten whole containers or opaque widget backgrounds.
 */
final class WidgetComponentWhiteningPolicy {
    private WidgetComponentWhiteningPolicy() {}

    static boolean supports(WidgetComponentStore.Descriptor descriptor) {
        if (descriptor == null || !descriptor.isRemoteViews()) return false;
        return WidgetComponentStore.TYPE_TEXT.equals(descriptor.componentType)
                        && WidgetComponentStore.ACTION_HIDE_VIEW.equals(descriptor.action)
                || WidgetComponentStore.TYPE_IMAGE.equals(descriptor.componentType)
                        && WidgetComponentStore.ACTION_CLEAR_IMAGE.equals(descriptor.action);
    }

    static List<WidgetComponentStore.Descriptor> selectors(
            Set<String> encoded, String provider) {
        if (provider == null || encoded == null || encoded.isEmpty()) return List.of();
        ArrayList<WidgetComponentStore.Descriptor> results = new ArrayList<>();
        for (String key : encoded) {
            WidgetComponentStore.Descriptor selected = WidgetComponentStore.parseSelector(key);
            if (selected != null && provider.equals(selected.owner) && supports(selected)) {
                results.add(selected);
            }
        }
        return results;
    }
}
