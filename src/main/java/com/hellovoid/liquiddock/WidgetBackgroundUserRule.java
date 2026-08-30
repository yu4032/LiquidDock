package com.hellovoid.liquiddock;

import java.util.Objects;

/** Exact, user-selected widget background target discovered from a live Launcher 4.50 widget. */
public final class WidgetBackgroundUserRule {
    public enum TargetKind { MAML_ELEMENT, REMOTE_VIEWS_RESOURCE }

    private final WidgetBackgroundIdentity identity;
    private final TargetKind targetKind;
    private final String target;

    public WidgetBackgroundUserRule(
            WidgetBackgroundIdentity identity, TargetKind targetKind, String target) {
        if (identity == null) throw new IllegalArgumentException("identity == null");
        if (targetKind == null) throw new IllegalArgumentException("targetKind == null");
        if (target == null || target.isEmpty()) throw new IllegalArgumentException("target is empty");
        this.identity = identity;
        this.targetKind = targetKind;
        this.target = target;
    }

    public WidgetBackgroundIdentity identity() { return identity; }
    public TargetKind targetKind() { return targetKind; }
    public String target() { return target; }

    public boolean matches(WidgetBackgroundIdentity candidate) {
        if (candidate == null) return false;
        return Objects.equals(identity.type, candidate.type)
                && Objects.equals(identity.productId, candidate.productId)
                && Objects.equals(identity.appPackage, candidate.appPackage)
                && identity.spanX == candidate.spanX
                && identity.spanY == candidate.spanY
                && identity.configSpanX == candidate.configSpanX
                && identity.configSpanY == candidate.configSpanY;
    }
}
