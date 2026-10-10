package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Runtime config reader backed by API101 Remote Preferences. */
public class ConfigReader {
    public static final String REMOTE_GROUP = "config";

    private final Map<String, ?> prefs;

    ConfigReader(Map<String, ?> prefs) {
        this.prefs = new HashMap<>(prefs);
    }

    private ConfigReader(SharedPreferences remote) {
        Map<String, ?> all = remote.getAll();
        prefs = all == null ? Collections.emptyMap() : new HashMap<>(all);
    }

    private ConfigReader() {
        this(loadRemoteSnapshot());
    }

    private static Map<String, ?> loadRemoteSnapshot() {
        try {
            SharedPreferences remote = Api101Bridge.remotePreferences(REMOTE_GROUP);
            Map<String, ?> all = remote.getAll();
            if (all != null && !all.isEmpty()) {
                Map<String, ?> loaded = new HashMap<>(all);
                Api101Bridge.log("config loaded from API101 Remote Preferences: "
                        + loaded.size() + " keys");
                return loaded;
            }
            Api101Bridge.log("API101 Remote Preferences are empty; using defaults");
        } catch (Throwable error) {
            // Runtime config loading is deliberately read-only.
            Api101Bridge.log("API101 Remote Preferences unavailable; using defaults", error);
        }
        return Collections.emptyMap();
    }

    public static ConfigReader load() { return new ConfigReader(); }

    static ConfigReader load(SharedPreferences remote) { return new ConfigReader(remote); }

    /** Content identity for deciding whether a long-lived injected runtime must refresh. */
    int snapshotHash() { return prefs.hashCode(); }

    /** Copy-on-write overlay for one app's independent optics, never mutating global config. */
    ConfigReader withOverrides(Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) return this;
        HashMap<String, Object> combined = new HashMap<>(prefs);
        combined.putAll(updates);
        return new ConfigReader(combined);
    }

    public boolean has(String key) { return prefs.containsKey(key); }

    public String s(String key, String def) {
        Object value = prefs.get(key);
        return value != null ? String.valueOf(value) : def;
    }

    public Set<String> stringSet(String key) {
        Object value = prefs.get(key);
        if (!(value instanceof Set)) return Collections.emptySet();
        HashSet<String> result = new HashSet<>();
        for (Object item : (Set<?>) value) {
            if (item instanceof String) result.add((String) item);
        }
        return Collections.unmodifiableSet(result);
    }

    public int i(String key, int def) {
        Object value = prefs.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); }
            catch (NumberFormatException ignored) {}
        }
        return def;
    }

    public float f(String key, float def) {
        // Compose persists decimal-dp settings losslessly in <key>_tenths.
        Object tenths = prefs.get(key + "_tenths");
        if (tenths instanceof Number) return ((Number) tenths).intValue() / 10f;
        if (tenths instanceof String) {
            try { return Integer.parseInt((String) tenths) / 10f;
            } catch (NumberFormatException ignored) {}
        }

        Object value = prefs.get(key);
        if (value instanceof Number) return ((Number) value).floatValue();
        if (value instanceof String) {
            try { return Float.parseFloat((String) value); }
            catch (NumberFormatException ignored) {}
        }
        return def;
    }

    public boolean b(String key, boolean def) {
        Object value = prefs.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return def;
    }
}
