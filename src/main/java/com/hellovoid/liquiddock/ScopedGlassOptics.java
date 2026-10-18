package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigKey;
import com.hellovoid.liquiddock.config.ConfigSchema;

import java.util.ArrayList;
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
    public static final String POPUP = "popup";

    // Popup-only controls deliberately do not add duplicate global material keys.
    private static final List<ConfigKey<Integer>> POPUP_BASICS = Collections.unmodifiableList(
            Arrays.asList(ConfigSchema.Glass.BLUR, ConfigSchema.Glass.TINT_RED,
                    ConfigSchema.Glass.TINT_GREEN, ConfigSchema.Glass.TINT_BLUE,
                    ConfigSchema.Glass.TINT_ALPHA));
    private static final List<ConfigKey<Boolean>> POPUP_HIGHLIGHTS = Collections.unmodifiableList(
            Arrays.asList(ConfigSchema.LauncherHighlight.LARGE_SKY_HAZE,
                    ConfigSchema.LauncherHighlight.LARGE_CAUSTICS,
                    ConfigSchema.LauncherHighlight.LARGE_PRESS_GLOW));

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

    public static List<ConfigKey<Integer>> keysForScope(String scope) {
        validateScope(scope);
        if (!POPUP.equals(scope)) return OPTICAL_KEYS;
        List<ConfigKey<Integer>> keys = new ArrayList<>(POPUP_BASICS);
        keys.addAll(OPTICAL_KEYS);
        return Collections.unmodifiableList(keys);
    }

    public static List<ConfigKey<Boolean>> popupHighlightKeys() {
        return POPUP_HIGHLIGHTS;
    }

    public static String popupHighlightKey(ConfigKey<Boolean> original) {
        if (!POPUP_HIGHLIGHTS.contains(original)) {
            throw new IllegalArgumentException("not a popup highlight key: " + original.name());
        }
        return prefix(POPUP, original.name());
    }

    public static String key(String scope, ConfigKey<Integer> original) {
        validateScope(scope);
        if (!OPTICAL_KEYS.contains(original)
                && !(POPUP.equals(scope) && POPUP_BASICS.contains(original))) {
            throw new IllegalArgumentException("not a scoped glass key: " + original.name());
        }
        return prefix(scope, original.name());
    }

    private static String prefix(String scope, String globalName) {
        if (!globalName.startsWith("liquid_")) {
            throw new IllegalArgumentException("unsupported global glass key: " + globalName);
        }
        return "liquid_scope_" + scope + "_" + globalName.substring("liquid_".length());
    }

    public static String normalsKey(String scope) {
        validateScope(scope);
        return "liquid_scope_" + scope + "_prismal_show_normals";
    }

    private static void validateScope(String scope) {
        if (!GBOARD.equals(scope) && !SEARCHBOX.equals(scope) && !DIALOG.equals(scope) && !POPUP.equals(scope)) {
            throw new IllegalArgumentException("unsupported scope: " + scope);
        }
    }

    public static LiquidDockConfig.Glass resolve(
            ConfigReader reader, LiquidDockConfig.Glass inherited, String scope) {
        if (reader == null || inherited == null) return inherited;
        validateScope(scope);
        Map<String, Object> updates = new HashMap<>();
        for (ConfigKey<Integer> config : keysForScope(scope)) {
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
        if (POPUP.equals(scope)) {
            for (ConfigKey<Boolean> config : POPUP_HIGHLIGHTS) {
                String scopedKey = popupHighlightKey(config);
                if (reader.has(scopedKey)) {
                    updates.put(config.name(), reader.b(scopedKey, config.runtimeFallback()));
                }
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
