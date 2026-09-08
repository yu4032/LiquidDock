package com.hellovoid.liquiddock;

/** Owns the Widget-specific adaptation decision for the custom Home grid. */
final class HomeGridWidgetAdaptationHook {
    private final boolean adaptationEnabled;

    HomeGridWidgetAdaptationHook(boolean adaptationEnabled) {
        this.adaptationEnabled = adaptationEnabled;
    }

    boolean shouldAdapt(Object itemInfo, int spanX, int spanY) {
        return adaptationEnabled
                && WidgetClassifier.isWidget(itemInfo)
                && WidgetSpecRegistry.DEFAULT.supports(spanX, spanY);
    }
}
