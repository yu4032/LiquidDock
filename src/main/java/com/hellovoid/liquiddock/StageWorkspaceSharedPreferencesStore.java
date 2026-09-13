package com.hellovoid.liquiddock;

import android.content.SharedPreferences;

/** Synchronous persistence boundary for Stage migration targets. */
final class StageWorkspaceSharedPreferencesStore implements HomeGridOrientationMemoryStore {
    private final SharedPreferences preferences;

    StageWorkspaceSharedPreferencesStore(SharedPreferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("preferences == null");
        this.preferences = preferences;
    }

    @Override
    public String read(String key) {
        return preferences.getString(key, null);
    }

    @Override
    public void write(String key, String value) {
        if (!preferences.edit().putString(key, value).commit()) {
            throw new IllegalStateException("Stage target commit failed");
        }
    }

    @Override
    public void remove(String key) {
        if (!preferences.edit().remove(key).commit()) {
            throw new IllegalStateException("Stage target removal failed");
        }
    }
}
