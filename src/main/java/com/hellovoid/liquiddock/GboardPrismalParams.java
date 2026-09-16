package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalParams;

/** Compatibility adapter for Gboard's public appearance model. */
final class GboardPrismalParams {
    private GboardPrismalParams() {}

    static PrismalParams apply(
            PrismalParams base, GboardGlassPreferences.Appearance appearance) {
        if (base == null || appearance == null) return base;
        return ThirdPartyPrismalParams.apply(base, appearance.toShared(null));
    }
}
