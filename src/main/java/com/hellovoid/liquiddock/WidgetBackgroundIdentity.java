package com.hellovoid.liquiddock;

/** Immutable identity fields used to select a widget-background rule. */
public final class WidgetBackgroundIdentity {
    public final String type;
    public final String productId;
    public final String appPackage;
    public final int spanX;
    public final int spanY;
    public final int configSpanX;
    public final int configSpanY;

    public WidgetBackgroundIdentity(
            String type,
            String productId,
            String appPackage,
            int spanX,
            int spanY,
            int configSpanX,
            int configSpanY) {
        this.type = type;
        this.productId = productId;
        this.appPackage = appPackage;
        this.spanX = spanX;
        this.spanY = spanY;
        this.configSpanX = configSpanX;
        this.configSpanY = configSpanY;
    }
}
