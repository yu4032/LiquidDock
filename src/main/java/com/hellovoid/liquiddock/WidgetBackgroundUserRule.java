package com.hellovoid.liquiddock;

import java.util.Objects;

/** Exact, user-selected widget background target discovered from a live Launcher 4.50 widget. */
final class WidgetBackgroundUserRule {
    enum TargetKind { MAML_ELEMENT, REMOTE_VIEWS_RESOURCE }

    private final WidgetBackgroundIdentity identity;
    private final TargetKind targetKind;
    private final String target;

    WidgetBackgroundUserRule(
            WidgetBackgroundIdentity identity, TargetKind targetKind, String target) {
        if (identity == null) throw new IllegalArgumentException("identity == null");
        if (targetKind == null) throw new IllegalArgumentException("targetKind == null");
        if (target == null || target.isEmpty()) throw new IllegalArgumentException("target is empty");
        this.identity = identity;
        this.targetKind = targetKind;
        this.target = target;
    }

    WidgetBackgroundIdentity identity() { return identity; }
    TargetKind targetKind() { return targetKind; }
    String target() { return target; }

    boolean matches(WidgetBackgroundIdentity candidate) {
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
