package com.hellovoid.liquiddock;

/** Local settings-app color choices. Not part of Launcher/Hook preferences or JSON export. */
enum SettingsUiPalette {
    SYSTEM("system", null),
    BLUE("blue", 0xFF007AFF),
    PURPLE("purple", 0xFFAF52DE),
    PINK("pink", 0xFFFF2D55),
    RED("red", 0xFFFF3B30),
    ORANGE("orange", 0xFFFF9500),
    GREEN("green", 0xFF34C759);

    private final String persistedValue;
    private final Integer seedArgb;

    SettingsUiPalette(String persistedValue, Integer seedArgb) {
        this.persistedValue = persistedValue;
        this.seedArgb = seedArgb;
    }

    String getPersistedValue() { return persistedValue; }
    Integer getSeedArgb() { return seedArgb; }

    static SettingsUiPalette fromStored(String value) {
        if (value != null) {
            for (SettingsUiPalette palette : values()) {
                if (palette.persistedValue.equals(value)) return palette;
            }
        }
        return SYSTEM;
    }
}
