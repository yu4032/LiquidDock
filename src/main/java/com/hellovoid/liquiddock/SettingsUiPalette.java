package com.hellovoid.liquiddock;

/** Local settings-app color choices. Not part of Launcher/Hook preferences or JSON export. */
enum SettingsUiPalette {
    SYSTEM("system", null),
    BLUE("blue", 0xFF3478F6),
    PURPLE("purple", 0xFF9B67DD),
    PINK("pink", 0xFFEB62B2),
    RED("red", 0xFFE4565D),
    ORANGE("orange", 0xFFF28B35),
    GREEN("green", 0xFF37A575);

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
