package com.hellovoid.liquiddock.config;

import com.hellovoid.liquiddock.ScopedGlassOptics;

import java.util.LinkedHashMap;
import java.util.Map;

/** Allowlisted backup contract for independent per-scope optics. */
final class ScopedGlassConfigCodec {
    private static final String[] SCOPES = {
            ScopedGlassOptics.GBOARD,
            ScopedGlassOptics.SEARCHBOX,
            ScopedGlassOptics.DIALOG
    };

    private ScopedGlassConfigCodec() {}

    static void exportInto(Map<String, ?> prefs, LinkedHashMap<String, Object> out) {
        if (prefs == null || out == null) return;
        for (String scope : SCOPES) {
            for (ConfigKey<Integer> config : ScopedGlassOptics.opticalKeys()) {
                String key = ScopedGlassOptics.key(scope, config);
                Object raw = prefs.get(key);
                if (config.storageMode() == ConfigKey.StorageMode.DP_TENTHS
                        && prefs.get(key + "_tenths") instanceof Number) {
                    out.put(key, ((Number) prefs.get(key + "_tenths")).intValue() / 10.0d);
                } else if (raw instanceof Number) {
                    out.put(key, raw);
                }
            }
            String normals = ScopedGlassOptics.normalsKey(scope);
            if (prefs.get(normals) instanceof Boolean) {
                out.put(normals, prefs.get(normals));
            }
        }
    }

    static void importInto(Map<String, ?> values, LinkedHashMap<String, Object> out) {
        if (values == null || out == null) return;
        for (String scope : SCOPES) {
            for (ConfigKey<Integer> config : ScopedGlassOptics.opticalKeys()) {
                String key = ScopedGlassOptics.key(scope, config);
                Object value = values.get(key);
                if (!(value instanceof Number)) continue;
                double raw = ((Number) value).doubleValue();
                if (!Double.isFinite(raw)) continue;
                double bounded = Math.max(config.minInt(), Math.min(config.maxInt(), raw));
                if (config.storageMode() == ConfigKey.StorageMode.DP_TENTHS) {
                    int tenths = (int) Math.round(bounded * 10.0d);
                    out.put(key, (int) Math.round(tenths / 10.0d));
                    out.put(key + "_tenths", tenths);
                } else {
                    out.put(key, (int) Math.round(bounded));
                }
            }
            String normals = ScopedGlassOptics.normalsKey(scope);
            if (values.get(normals) instanceof Boolean) {
                out.put(normals, values.get(normals));
            }
        }
    }
}
