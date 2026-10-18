package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigKey;
import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.prismal.PrismalEdgeDecoration;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One per-process immutable preference snapshot for exterior SDF decorations.
 *
 * Presence of a scoped setting overrides only that field, including explicit false.
 * Prismal's upstream innerShadow and OS4 optical edge keep their original semantics.
 */
public final class GlassEdgeEffects {
    public static final String GLOBAL = "global";
    public static final String ICON = "icon";
    public static final String WIDGET = "widget";
    public static final String FOLDER = "folder";
    public static final String POPUP = "popup";
    public static final String HANDLE_MENU = "handle_menu";
    public static final String DIALOG = "dialog";
    public static final String GBOARD = "gboard";
    public static final String SEARCHBOX = "searchbox";
    public static final String SECURITY_CENTER = "security_center";

    public static final List<String> SCOPES = Collections.unmodifiableList(Arrays.asList(
            ICON, WIDGET, FOLDER, POPUP, HANDLE_MENU, DIALOG, GBOARD,
            SEARCHBOX, SECURITY_CENTER));
    public static final List<ConfigKey<?>> KEYS = Collections.unmodifiableList(Arrays.asList(
            ConfigSchema.Glass.OUTER_SHADOW_ENABLED,
            ConfigSchema.Glass.OUTER_SHADOW_RADIUS,
            ConfigSchema.Glass.OUTER_SHADOW_OFFSET_Y,
            ConfigSchema.Glass.OUTER_SHADOW_RED,
            ConfigSchema.Glass.OUTER_SHADOW_GREEN,
            ConfigSchema.Glass.OUTER_SHADOW_BLUE,
            ConfigSchema.Glass.OUTER_SHADOW_ALPHA,
            ConfigSchema.Glass.OUTER_SHADOW_LIGHT_FOLLOW,
            ConfigSchema.Glass.OUTER_STROKE_ENABLED,
            ConfigSchema.Glass.OUTER_STROKE_WIDTH,
            ConfigSchema.Glass.OUTER_STROKE_RED,
            ConfigSchema.Glass.OUTER_STROKE_GREEN,
            ConfigSchema.Glass.OUTER_STROKE_BLUE,
            ConfigSchema.Glass.OUTER_STROKE_ALPHA,
            ConfigSchema.Glass.OUTER_STROKE_LIGHT_RESPONSE));

    private static volatile Map<String, PrismalEdgeDecoration> current =
            Collections.emptyMap();

    private GlassEdgeEffects() {}

    public static String key(String scope, ConfigKey<?> config) {
        if (!SCOPES.contains(scope) || !KEYS.contains(config)) {
            throw new IllegalArgumentException("unsupported glass edge field: " + scope);
        }
        return "liquid_scope_" + scope + "_"
                + config.name().substring("liquid_".length());
    }

    static PrismalEdgeDecoration resolve(ConfigReader reader, String scope) {
        if (!GLOBAL.equals(scope) && !SCOPES.contains(scope)) {
            throw new IllegalArgumentException("unsupported glass effect scope: " + scope);
        }
        if (reader == null) return PrismalEdgeDecoration.NONE;
        boolean stroke = bool(reader, scope, ConfigSchema.Glass.OUTER_STROKE_ENABLED);
        boolean shadow = bool(reader, scope, ConfigSchema.Glass.OUTER_SHADOW_ENABLED);
        return new PrismalEdgeDecoration(
                stroke ? decimal(reader, scope, ConfigSchema.Glass.OUTER_STROKE_WIDTH) : 0f,
                integer(reader, scope, ConfigSchema.Glass.OUTER_STROKE_RED) / 255f,
                integer(reader, scope, ConfigSchema.Glass.OUTER_STROKE_GREEN) / 255f,
                integer(reader, scope, ConfigSchema.Glass.OUTER_STROKE_BLUE) / 255f,
                stroke ? integer(reader, scope, ConfigSchema.Glass.OUTER_STROKE_ALPHA) / 255f : 0f,
                shadow ? decimal(reader, scope, ConfigSchema.Glass.OUTER_SHADOW_RADIUS) : 0f,
                decimal(reader, scope, ConfigSchema.Glass.OUTER_SHADOW_OFFSET_Y),
                integer(reader, scope, ConfigSchema.Glass.OUTER_SHADOW_RED) / 255f,
                integer(reader, scope, ConfigSchema.Glass.OUTER_SHADOW_GREEN) / 255f,
                integer(reader, scope, ConfigSchema.Glass.OUTER_SHADOW_BLUE) / 255f,
                shadow ? integer(reader, scope, ConfigSchema.Glass.OUTER_SHADOW_ALPHA) / 255f : 0f,
                integer(reader, scope, ConfigSchema.Glass.OUTER_SHADOW_LIGHT_FOLLOW) / 100f,
                integer(reader, scope, ConfigSchema.Glass.OUTER_STROKE_LIGHT_RESPONSE) / 100f);
    }

    public static synchronized void refresh(ConfigReader reader) {
        Map<String, PrismalEdgeDecoration> resolved = new HashMap<>();
        resolved.put(GLOBAL, resolve(reader, GLOBAL));
        for (String scope : SCOPES) resolved.put(scope, resolve(reader, scope));
        current = Collections.unmodifiableMap(resolved);
    }

    public static PrismalEdgeDecoration current(String scope) {
        PrismalEdgeDecoration style = current.get(scope);
        return style == null ? PrismalEdgeDecoration.NONE : style;
    }

    /** On surfaces with a native shadow owner, do not draw a second GPU halo. */
    public static PrismalEdgeDecoration strokeOnly(String scope) {
        return current(scope).strokeOnly();
    }

    private static String readKey(ConfigReader reader, String scope, ConfigKey<?> config) {
        if (!GLOBAL.equals(scope)) {
            String local = key(scope, config);
            if (reader.has(local) || reader.has(local + "_tenths")) return local;
        }
        return config.name();
    }

    private static boolean bool(ConfigReader reader, String scope, ConfigKey<Boolean> config) {
        return reader.b(readKey(reader, scope, config), config.runtimeFallback());
    }

    private static int integer(ConfigReader reader, String scope, ConfigKey<Integer> config) {
        return Math.max(config.minInt(), Math.min(config.maxInt(),
                reader.i(readKey(reader, scope, config), config.runtimeFallback())));
    }

    private static float decimal(ConfigReader reader, String scope, ConfigKey<Integer> config) {
        return Math.max(config.minInt(), Math.min(config.maxInt(),
                reader.f(readKey(reader, scope, config), config.runtimeFallback())));
    }
}
