package com.hellovoid.liquiddock;

/** Persisted third-party adapter switch for MIUI Searchbox. */
final class MiuiSearchboxGlassPreferences {
    static final String ENABLED_KEY = "liquid_miui_searchbox_glass";
    static final boolean ENABLED_DEFAULT = true;

    private MiuiSearchboxGlassPreferences() {}

    static boolean isEnabled(ConfigReader reader) {
        if (reader == null) reader = ConfigReader.load();
        return reader.b(ENABLED_KEY, ENABLED_DEFAULT);
    }
}
