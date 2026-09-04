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
                ConfigSchema.LauncherHighlight.SPECULAR,
                ConfigSchema.LauncherHighlight.LIT_RIM,
                ConfigSchema.LauncherHighlight.OPPOSITE_RIM,
                ConfigSchema.LauncherHighlight.CORNER_RIM,
                ConfigSchema.LauncherHighlight.FACE_SHEEN,
                ConfigSchema.LauncherHighlight.PLAIN_HIGHLIGHT,
                ConfigSchema.LauncherHighlight.CAUSTICS,
                ConfigSchema.LauncherHighlight.PRESS_GLOW);
    }

    static PrismalHighlightProfile readLargeSurfaces(ConfigReader c) {
        return readProfile(c,
                ConfigSchema.LauncherHighlight.LARGE_SKY_HAZE,
                ConfigSchema.LauncherHighlight.LARGE_SPECULAR,
                ConfigSchema.LauncherHighlight.LARGE_LIT_RIM,
                ConfigSchema.LauncherHighlight.LARGE_OPPOSITE_RIM,
                ConfigSchema.LauncherHighlight.LARGE_CORNER_RIM,
                ConfigSchema.LauncherHighlight.LARGE_FACE_SHEEN,
                ConfigSchema.LauncherHighlight.LARGE_PLAIN_HIGHLIGHT,
                ConfigSchema.LauncherHighlight.LARGE_CAUSTICS,
                ConfigSchema.LauncherHighlight.LARGE_PRESS_GLOW);
    }

    @SafeVarargs
    private static PrismalHighlightProfile readProfile(
            ConfigReader c, ConfigKey<Boolean>... keys) {
        if (keys.length != 9) throw new IllegalArgumentException("highlight key count != 9");
        return new PrismalHighlightProfile(
                read(c, keys[0]), read(c, keys[1]), read(c, keys[2]),
                read(c, keys[3]), read(c, keys[4]), read(c, keys[5]),
                read(c, keys[6]), read(c, keys[7]), read(c, keys[8]));
    }

    private static boolean read(ConfigReader c, ConfigKey<Boolean> key) {
        return c.b(key.name(), key.runtimeFallback());
    }
}
