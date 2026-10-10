package com.hellovoid.liquiddock.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure transformations between preference writes and the historical JSON value contract. */
public final class ConfigCodec {
    private ConfigCodec() {}

    public static LinkedHashMap<String, Object> exportValues(Map<String, ?> preferences) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (ConfigKey<?> key : ConfigSchema.all()) {
            if (key.exportMode() == ConfigKey.ExportMode.NEVER) continue;
            if (key.exportMode() == ConfigKey.ExportMode.IF_PRESENT
                    && !preferences.containsKey(key.name())) continue;
            out.put(key.name(), exportValue(key, preferences));
        }
        ThirdPartyGlassConfigCodec.exportInto(preferences, out);
        ScopedGlassConfigCodec.exportInto(preferences, out);
        return out;
    }

    public static LinkedHashMap<String, Object> importValues(Map<String, ?> jsonValues) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (ConfigKey<?> key : ConfigSchema.all()) {
            if (!isDirectlyImportable(key) || !jsonValues.containsKey(key.name())) continue;
            importValue(key, jsonValues.get(key.name()), out);
        }
        ThirdPartyGlassConfigCodec.importInto(jsonValues, out);
        ScopedGlassConfigCodec.importInto(jsonValues, out);
        return out;
    }

    private static Object exportValue(ConfigKey<?> key, Map<String, ?> preferences) {
        if (key.storageMode() == ConfigKey.StorageMode.DP_TENTHS) {
            Object tenths = preferences.get(key.name() + "_tenths");
            if (preferences.containsKey(key.name() + "_tenths") && tenths instanceof Number) {
                return ((Number) tenths).intValue() / 10.0d;
            }
        }
        Object value = preferences.get(key.name());
        return value == null ? key.exportDefault() : value;
    }

    private static boolean isDirectlyImportable(ConfigKey<?> key) {
        return key.exportMode() != ConfigKey.ExportMode.NEVER;
    }

    private static void importValue(ConfigKey<?> key, Object value, Map<String, Object> out) {
        if (key.storageMode() == ConfigKey.StorageMode.DP_TENTHS) {
            importDp(key, value, out);
        } else if (key.type() == ConfigKey.Type.BOOLEAN) {
            out.put(key.name(), booleanValue(value));
        } else if (key.type() == ConfigKey.Type.INT && value instanceof Number) {
            out.put(key.name(), clamp(((Number) value).intValue(), key.minInt(), key.maxInt()));
        } else if (key.type() == ConfigKey.Type.STRING && value != null) {
            out.put(key.name(), String.valueOf(value));
        }
    }

    private static void importDp(ConfigKey<?> key, Object value, Map<String, Object> out) {
        if (!(value instanceof Number)) return;
        double dp = ((Number) value).doubleValue();
        if (!Double.isFinite(dp)) return;
        if (key.minInt() != null) dp = Math.max(key.minInt(), dp);
        if (key.maxInt() != null) dp = Math.min(key.maxInt(), dp);
        out.put(key.name(), (int) Math.round(dp));
        out.put(key.name() + "_tenths", (int) Math.round(dp * 10.0d));
    }

    private static int clamp(int value, Integer min, Integer max) {
        if (min != null && value < min) return min;
        if (max != null && value > max) return max;
        return value;
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
    }
}
