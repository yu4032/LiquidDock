package com.hellovoid.liquiddock.os4

import com.hellovoid.liquiddock.WidgetComponentRanking
import com.hellovoid.liquiddock.WidgetComponentStore

/**
 * OS4 background suppression is owned by widget glass. Explicit non-background
 * MAML hide selections remain independent of the material switch.
 *
 * Rank against the exact discovered catalog descriptor (which carries render
 * order, area and depth), not the metadata-free saved selector.
 */
internal object Os4MamlHidePolicy {
    fun shouldApply(
        descriptor: WidgetComponentStore.Descriptor,
        widgetGlassEnabled: Boolean,
    ): Boolean = widgetGlassEnabled ||
        (descriptor.componentType != WidgetComponentStore.TYPE_BACKGROUND &&
            !WidgetComponentRanking.isLikelyBackground(descriptor))
}
