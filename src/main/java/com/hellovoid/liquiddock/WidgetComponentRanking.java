package com.hellovoid.liquiddock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure ranking policy for surfacing likely bottom/background widget components first. */
public final class WidgetComponentRanking {
    private static final int LIKELY_BACKGROUND_THRESHOLD = 80;

    private WidgetComponentRanking() {}

    public static int backgroundScore(WidgetComponentStore.Descriptor descriptor) {
        if (descriptor == null || descriptor.renderOrdinal < 0 || descriptor.depth < 0) return 0;

        int score = 0;
        switch (descriptor.componentType) {
            case WidgetComponentStore.TYPE_BACKGROUND:
                score += 50;
                break;
            case WidgetComponentStore.TYPE_IMAGE:
                score += 20;
                break;
            case WidgetComponentStore.TYPE_CONTAINER:
                score += 8;
                break;
            case WidgetComponentStore.TYPE_TEXT:
            case WidgetComponentStore.TYPE_INTERACTIVE:
            case WidgetComponentStore.TYPE_INTERNAL:
                score -= 30;
                break;
            default:
                break;
        }

        if (!Float.isNaN(descriptor.areaRatio)) {
            if (descriptor.areaRatio >= 0.85f) score += 30;
            else if (descriptor.areaRatio >= 0.60f) score += 24;
            else if (descriptor.areaRatio >= 0.35f) score += 16;
            else if (descriptor.areaRatio >= 0.15f) score += 6;
        }

        if (descriptor.renderOrdinal == 0) score += 20;
        else if (descriptor.renderOrdinal <= 2) score += 15;
        else if (descriptor.renderOrdinal <= 5) score += 10;
        else if (descriptor.renderOrdinal <= 10) score += 5;

        if (descriptor.depth == 0) score += 15;
        else if (descriptor.depth == 1) score += 10;
        else if (descriptor.depth == 2) score += 5;
        else if (descriptor.depth >= 5) score -= 5;

        if (!Float.isNaN(descriptor.effectiveZ)) {
            if (descriptor.effectiveZ <= 0f) score += 5;
            else if (descriptor.effectiveZ >= 8f) score -= 8;
            else if (descriptor.effectiveZ >= 2f) score -= 4;
        }
        return score;
    }

    public static boolean isLikelyBackground(WidgetComponentStore.Descriptor descriptor) {
        return backgroundScore(descriptor) >= LIKELY_BACKGROUND_THRESHOLD;
    }

    public static int compare(
            WidgetComponentStore.Descriptor left,
            WidgetComponentStore.Descriptor right) {
        boolean leftLikely = isLikelyBackground(left);
        boolean rightLikely = isLikelyBackground(right);
        if (leftLikely != rightLikely) return leftLikely ? -1 : 1;

        int byScore = Integer.compare(backgroundScore(right), backgroundScore(left));
        if (byScore != 0) return byScore;

        int byOrdinal = Integer.compare(normalizeOrdinal(left), normalizeOrdinal(right));
        if (byOrdinal != 0) return byOrdinal;

        int byDepth = Integer.compare(normalizeDepth(left), normalizeDepth(right));
        if (byDepth != 0) return byDepth;

        int byName = left.name.compareTo(right.name);
        if (byName != 0) return byName;
        return left.className.compareTo(right.className);
    }

    public static List<WidgetComponentStore.Descriptor> sorted(
            List<WidgetComponentStore.Descriptor> descriptors) {
        ArrayList<WidgetComponentStore.Descriptor> result =
                new ArrayList<>(descriptors == null ? java.util.Collections.emptyList() : descriptors);
        result.sort(new Comparator<WidgetComponentStore.Descriptor>() {
            @Override public int compare(
                    WidgetComponentStore.Descriptor left,
                    WidgetComponentStore.Descriptor right) {
                return WidgetComponentRanking.compare(left, right);
            }
        });
        return result;
    }

    private static int normalizeOrdinal(WidgetComponentStore.Descriptor descriptor) {
        return descriptor == null || descriptor.renderOrdinal < 0
                ? Integer.MAX_VALUE : descriptor.renderOrdinal;
    }

    private static int normalizeDepth(WidgetComponentStore.Descriptor descriptor) {
        return descriptor == null || descriptor.depth < 0
                ? Integer.MAX_VALUE : descriptor.depth;
    }
}
