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

    /** Treat different actions on the same exact widget node as one mutually exclusive target. */
    static boolean sameNode(WidgetComponentStore.Descriptor first,
                            WidgetComponentStore.Descriptor second) {
        return first != null && second != null
                && first.isRemoteViews() && second.isRemoteViews()
                && first.owner.equals(second.owner)
                && first.className.equals(second.className)
                && first.name.equals(second.name)
                && first.hierarchyPath.equals(second.hierarchyPath);
    }

    static List<WidgetComponentStore.Descriptor> selectors(
            Set<String> encoded, String provider) {
        if (provider == null || encoded == null || encoded.isEmpty()) return List.of();
        ArrayList<WidgetComponentStore.Descriptor> results = new ArrayList<>();
        for (String key : encoded) {
            WidgetComponentStore.Descriptor selected = WidgetComponentStore.parseSelector(key);
            // The persisted R2 selector encodes action/path/class, not componentType.
            // Resolve the actual TextView/ImageView type at the runtime target.
            if (selected != null && provider.equals(selected.owner) && selected.isRemoteViews()
                    && (WidgetComponentStore.ACTION_HIDE_VIEW.equals(selected.action)
                    || WidgetComponentStore.ACTION_CLEAR_IMAGE.equals(selected.action))) {
                results.add(selected);
            }
        }
        return results;
    }
}
