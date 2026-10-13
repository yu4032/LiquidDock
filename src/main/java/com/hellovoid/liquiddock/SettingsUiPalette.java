package com.hellovoid.liquiddock;

/**
 * Apple HIG default iOS/iPadOS system colors (light and dark appearance).
 * Values are fixed sRGB references, not Apple's trait-aware UIColor objects;
 * Android follows its current light/dark mode explicitly.
 *
 * Only used by the settings UI. Never persisted into Launcher/Hook configuration.
 * https://developer.apple.com/design/human-interface-guidelines/color
 */
enum SettingsUiPalette {
    SYSTEM("system", null, null),
    BLUE("blue", 0xFF0088FF, 0xFF0091FF),
    PURPLE("purple", 0xFFCB30E0, 0xFFDB34F2),
    PINK("pink", 0xFFFF2D55, 0xFFFF375F),
    RED("red", 0xFFFF383C, 0xFFFF4245),
    ORANGE("orange", 0xFFFF8D28, 0xFFFF9230),
    GREEN("green", 0xFF34C759, 0xFF30D158);

    private final String persistedValue;
    private final Integer lightArgb;
    private final Integer darkArgb;

    SettingsUiPalette(String persistedValue, Integer lightArgb, Integer darkArgb) {
        this.persistedValue = persistedValue;
        this.lightArgb = lightArgb;
        this.darkArgb = darkArgb;
    }

    String getPersistedValue() { return persistedValue; }

    /** Null for SYSTEM: use Android wallpaper dynamic color instead. */
    Integer getSeedArgb(boolean dark) { return dark ? darkArgb : lightArgb; }

    static SettingsUiPalette fromStored(String value) {
        if (value != null) {
            for (SettingsUiPalette palette : values()) {
                if (palette.persistedValue.equals(value)) return palette;
            }
        }
        return SYSTEM;
    }
}
