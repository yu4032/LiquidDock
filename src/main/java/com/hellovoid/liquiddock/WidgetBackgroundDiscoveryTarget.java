package com.hellovoid.liquiddock;

/** One exact live widget element/control that the user may choose to hide. */
public final class WidgetBackgroundDiscoveryTarget {
    private final WidgetBackgroundUserRule.TargetKind kind;
    private final String name;
    private final String detail;

    public WidgetBackgroundDiscoveryTarget(
            WidgetBackgroundUserRule.TargetKind kind, String name, String detail) {
        if (kind == null) throw new IllegalArgumentException("kind == null");
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("name is empty");
        this.kind = kind;
        this.name = name;
        this.detail = detail == null ? "" : detail;
    }

    public WidgetBackgroundUserRule.TargetKind kind() { return kind; }
    public String name() { return name; }
    public String detail() { return detail; }
}
