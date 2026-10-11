package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigKey;
import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.prismal.PrismalHighlightProfile;

/** Reads schema-owned component switches for compact and large Launcher glass surfaces. */
public final class LauncherHighlightPreferences {
    private LauncherHighlightPreferences() {}

    static PrismalHighlightProfile read(ConfigReader c) {
        return readProfile(c,
                ConfigSchema.LauncherHighlight.SKY_HAZE,
                ConfigSchema.LauncherHighlight.CAUSTICS,
                ConfigSchema.LauncherHighlight.PRESS_GLOW);
    }

    static PrismalHighlightProfile readLargeSurfaces(ConfigReader c) {
        return readProfile(c,
                ConfigSchema.LauncherHighlight.LARGE_SKY_HAZE,
                ConfigSchema.LauncherHighlight.LARGE_CAUSTICS,
                ConfigSchema.LauncherHighlight.LARGE_PRESS_GLOW);
    }

    @SafeVarargs
    private static PrismalHighlightProfile readProfile(
            ConfigReader c, ConfigKey<Boolean>... keys) {
        if (keys.length != 3) throw new IllegalArgumentException("highlight key count != 3");
        return new PrismalHighlightProfile(
                read(c, keys[0]), false, false, false, false, false, false,
                read(c, keys[1]), read(c, keys[2]));
    }

    private static boolean read(ConfigReader c, ConfigKey<Boolean> key) {
        return c.b(key.name(), key.runtimeFallback());
    }
}
