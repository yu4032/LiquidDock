package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalHighlightProfile;

/** Mutual exclusion between the OS4 background-driven edge and legacy highlight components. */
final class Os4EdgeModePolicy {
    private Os4EdgeModePolicy() {}

    static PrismalHighlightProfile effectiveHighlights(
            boolean os4SoftEdgeEnabled, PrismalHighlightProfile configured) {
        if (configured == null) configured = PrismalHighlightProfile.ALL_ENABLED;
        return os4SoftEdgeEnabled ? PrismalHighlightProfile.NONE_ENABLED : configured;
    }

    static boolean showOs4Controls(boolean os4SoftEdgeEnabled) {
        return os4SoftEdgeEnabled;
    }

    static boolean legacyHighlightsEnabled(boolean os4SoftEdgeEnabled) {
        return !os4SoftEdgeEnabled;
    }
}
