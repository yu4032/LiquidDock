package com.hellovoid.liquiddock;

import java.util.List;
import java.util.Objects;

/** One immutable declarative widget-background rule parsed from the bundled XML. */
final class WidgetBackgroundRule {
    private final String id;
    private final String type;
    private final String productId;
    private final String appPackage;
    private final Integer spanX;
    private final Integer spanY;
    private final Integer configSpanX;
    private final Integer configSpanY;
    private final List<String> elementNames;
    private final int specificity;

    WidgetBackgroundRule(
            String id,
            String type,
            String productId,
            String appPackage,
            Integer spanX,
            Integer spanY,
            Integer configSpanX,
            Integer configSpanY,
            List<String> elementNames) {
        this.id = id;
        this.type = type;
        this.productId = productId;
        this.appPackage = appPackage;
        this.spanX = spanX;
        this.spanY = spanY;
        this.configSpanX = configSpanX;
        this.configSpanY = configSpanY;
        this.elementNames = List.copyOf(elementNames);
        this.specificity = computeSpecificity();
    }

    String id() { return id; }

    List<String> elementNames() { return elementNames; }

    int specificity() { return specificity; }

    boolean matches(WidgetBackgroundIdentity identity) {
        if (identity == null) return false;
        return matches(type, identity.type)
                && matches(productId, identity.productId)
                && matches(appPackage, identity.appPackage)
                && matches(spanX, identity.spanX)
                && matches(spanY, identity.spanY)
                && matches(configSpanX, identity.configSpanX)
                && matches(configSpanY, identity.configSpanY);
    }

    private int computeSpecificity() {
        int score = 0;
        if (productId != null) score += 10_000;
        if (appPackage != null) score += 1_000;
        if (configSpanX != null) score += 100;
        if (configSpanY != null) score += 100;
        if (spanX != null) score += 10;
        if (spanY != null) score += 10;
        if (type != null) score += 1;
        return score;
    }

    private static boolean matches(String expected, String actual) {
        return expected == null || Objects.equals(expected, actual);
    }

    private static boolean matches(Integer expected, int actual) {
        return expected == null || expected == actual;
    }
}
