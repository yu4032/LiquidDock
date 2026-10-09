package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigKey;
import com.hellovoid.liquiddock.config.ConfigSchema;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Separate persisted Prismal optics for the three independently designed UI scopes.
 * Missing values inherit the entire current global glass profile; legacy per-scope
 * color and blur keys still take priority in their existing owners.
 */
public final class ScopedGlassOptics {
    public static final String GBOARD = "gboard";
    public static final String SEARCHBOX = "searchbox";
    public static final String DIALOG = "dialog";

    private static final List<ConfigKey<Integer>> OPTICAL_KEYS =
            Collections.unmodifiableList(Arrays.asList(
                    ConfigSchema.Glass.THICKNESS,
                    ConfigSchema.Glass.IOR,
                    ConfigSchema.Glass.NORMAL_STRENGTH,
                    ConfigSchema.Glass.DOME,
                    ConfigSchema.Glass.LENS_REFRACTION,
                    ConfigSchema.Glass.DEPTH_EFFECT,
                    ConfigSchema.Glass.CHROMATIC,
                    ConfigSchema.Glass.HIGHLIGHT_WIDTH,
                    ConfigSchema.Glass.BRIGHTNESS,
                    ConfigSchema.Glass.SPECULAR_SHARPNESS,
                    ConfigSchema.Glass.SPECULAR_STRENGTH,
                    ConfigSchema.Glass.RIM_LIGHT,
                    ConfigSchema.Glass.CAUSTICS,
                    ConfigSchema.Glass.PRISMAL_REFRACTION_INSET,
                    ConfigSchema.Glass.PRISMAL_DISPLACEMENT_SCALE,
                    ConfigSchema.Glass.PRISMAL_HEIGHT_TRANSITION_WIDTH,
                    ConfigSchema.Glass.PRISMAL_SMIN_SMOOTHING,
                    ConfigSchema.Glass.PRISMAL_EDGE_REFRACTION_FALLOFF,
                    ConfigSchema.Glass.PRISMAL_FRESNEL_REFLECT,
                    ConfigSchema.Glass.PRISMAL_DISPERSION_R,
                    ConfigSchema.Glass.PRISMAL_DISPERSION_B,
                    ConfigSchema.Glass.PRISMAL_VIBRANCY,
                    ConfigSchema.Glass.PRISMAL_PLAIN_HIGHLIGHT,
                    ConfigSchema.Glass.OS4_EDGE_WIDTH_PX,
                    ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX,
                    ConfigSchema.Glass.OS4_REFLECTION_STRENGTH,
                    ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN,
                    ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE,
                    ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY,
                    ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY,
                    ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X,
                    ConfigSchema.Glass.PRISMAL_LIGHT_DIR_Y,
                    ConfigSchema.Glass.PRISMAL_SHADOW_RED,
                    ConfigSchema.Glass.PRISMAL_SHADOW_GREEN,
                    ConfigSchema.Glass.PRISMAL_SHADOW_BLUE,
                    ConfigSchema.Glass.PRISMAL_SHADOW_ALPHA,
                    ConfigSchema.Glass.PRISMAL_SHADOW_SOFTNESS,
                    ConfigSchema.Glass.PRISMAL_TRANSMITTANCE,
                    ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_X,
                    ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_Y,
                    ConfigSchema.Glass.PRISMAL_PARALLAX_SCALE
            ));

    private ScopedGlassOptics() {}

    public static List<ConfigKey<Integer>> opticalKeys() {
        return OPTICAL_KEYS;
    }

    public static String key(String scope, ConfigKey<Integer> original) {
        validateScope(scope);
        if (!OPTICAL_KEYS.contains(original)) {
            throw new IllegalArgumentException("not an optical key: " + original.name());
        }
        return "liquid_scope_" + scope + "_" + original.name().substring("liquid_".length());
    }

    public static String normalsKey(String scope) {
        validateScope(scope);
        return "liquid_scope_" + scope + "_prismal_show_normals";
    }

    private static void validateScope(String scope) {
        if (!GBOARD.equals(scope) && !SEARCHBOX.equals(scope) && !DIALOG.equals(scope)) {
            throw new IllegalArgumentException("unsupported scope: " + scope);
        }
    }

    public static LiquidDockConfig.Glass resolve(
            ConfigReader reader, LiquidDockConfig.Glass inherited, String scope) {
        if (reader == null || inherited == null) return inherited;
        validateScope(scope);
        Map<String, Object> updates = new HashMap<>();
        for (ConfigKey<Integer> config : OPTICAL_KEYS) {
            String scopedKey = key(scope, config);
            if (reader.has(scopedKey)) {
                int bounded = Math.max(config.minInt(), Math.min(config.maxInt(),
                        reader.i(scopedKey, config.uiDefault())));
                updates.put(config.name(), bounded);
                // If a migrated single-int preference exists, it must not inherit the
                // global sidecar, which would otherwise outrank this local value.
                updates.put(config.name() + "_tenths", bounded * 10);
            }
            String tenthsKey = scopedKey + "_tenths";
            if (reader.has(tenthsKey)
                    && config.storageMode() == ConfigKey.StorageMode.DP_TENTHS) {
                int boundedTenths = Math.max(config.minInt() * 10,
                        Math.min(config.maxInt() * 10, reader.i(tenthsKey, config.uiDefault() * 10)));
                updates.put(config.name(), Math.round(boundedTenths / 10f));
                updates.put(config.name() + "_tenths", boundedTenths);
            }
        }
        String normals = normalsKey(scope);
        if (reader.has(normals)) {
            updates.put(ConfigSchema.Glass.PRISMAL_SHOW_NORMALS.name(),
                    reader.b(normals, ConfigSchema.Glass.PRISMAL_SHOW_NORMALS.uiDefault()));
        }
        return updates.isEmpty()
                ? inherited
                : LiquidDockConfig.from(reader.withOverrides(updates)).glass;
    }
}
