package com.hellovoid.liquiddock.config;

import com.hellovoid.liquiddock.ScopedGlassOptics;
import com.hellovoid.liquiddock.GlassEdgeEffects;

import java.util.LinkedHashMap;
import java.util.Map;

/** Allowlisted backup contract for independent per-scope optics. */
final class ScopedGlassConfigCodec {
    private static final String[] SCOPES = {
            ScopedGlassOptics.GBOARD,
            ScopedGlassOptics.SEARCHBOX,
            ScopedGlassOptics.DIALOG,
            ScopedGlassOptics.POPUP,
            ScopedGlassOptics.HANDLE_MENU
    };

    private ScopedGlassConfigCodec() {}

    static void exportInto(Map<String, ?> prefs, LinkedHashMap<String, Object> out) {
        if (prefs == null || out == null) return;
        for (String scope : GlassEdgeEffects.SCOPES) {
            for (ConfigKey<?> field : GlassEdgeEffects.KEYS) {
                String key = GlassEdgeEffects.key(scope, field);
                Object raw = prefs.get(key);
                if (field.storageMode() == ConfigKey.StorageMode.DP_TENTHS
                        && prefs.get(key + "_tenths") instanceof Number) {
                    out.put(key, ((Number) prefs.get(key + "_tenths")).intValue() / 10.0d);
                } else if (raw instanceof Boolean || raw instanceof Number) {
                    out.put(key, raw);
                }
            }
        }
        for (String scope : SCOPES) {
            for (ConfigKey<Integer> config : ScopedGlassOptics.keysForScope(scope)) {
                String key = ScopedGlassOptics.key(scope, config);
                Object raw = prefs.get(key);
                if (config.storageMode() == ConfigKey.StorageMode.DP_TENTHS
                        && prefs.get(key + "_tenths") instanceof Number) {
                    out.put(key, ((Number) prefs.get(key + "_tenths")).intValue() / 10.0d);
                } else if (raw instanceof Number) {
                    out.put(key, raw);
                }
            }
            if (ScopedGlassOptics.isMenuScope(scope)) {
                for (ConfigKey<Boolean> config : ScopedGlassOptics.menuHighlightKeys()) {
                    String highlight = ScopedGlassOptics.menuHighlightKey(scope, config);
                    if (prefs.get(highlight) instanceof Boolean) {
                        out.put(highlight, prefs.get(highlight));
                    }
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
        for (String scope : GlassEdgeEffects.SCOPES) {
            for (ConfigKey<?> field : GlassEdgeEffects.KEYS) {
                String key = GlassEdgeEffects.key(scope, field);
                Object raw = values.get(key);
                if (field.type() == ConfigKey.Type.BOOLEAN && raw instanceof Boolean) {
                    out.put(key, raw);
                } else if (field.type() == ConfigKey.Type.INT && raw instanceof Number) {
                    double v = ((Number) raw).doubleValue();
                    if (!Double.isFinite(v)) continue;
                    double bounded = Math.max(field.minInt(), Math.min(field.maxInt(), v));
                    if (field.storageMode() == ConfigKey.StorageMode.DP_TENTHS) {
                        int tenths = (int) Math.round(bounded * 10.0);
                        out.put(key, (int) Math.round(tenths / 10.0));
                        out.put(key + "_tenths", tenths);
                    } else {
                        out.put(key, (int) Math.round(bounded));
                    }
                }
            }
        }
        for (String scope : SCOPES) {
            for (ConfigKey<Integer> config : ScopedGlassOptics.keysForScope(scope)) {
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
            if (ScopedGlassOptics.isMenuScope(scope)) {
                for (ConfigKey<Boolean> config : ScopedGlassOptics.menuHighlightKeys()) {
                    String highlight = ScopedGlassOptics.menuHighlightKey(scope, config);
                    if (values.get(highlight) instanceof Boolean) {
                        out.put(highlight, values.get(highlight));
                    }
                }
            }
            String normals = ScopedGlassOptics.normalsKey(scope);
            if (values.get(normals) instanceof Boolean) {
                out.put(normals, values.get(normals));
            }
        }
    }
}
