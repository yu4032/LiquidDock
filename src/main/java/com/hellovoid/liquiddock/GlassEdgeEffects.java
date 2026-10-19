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
 * One immutable per-process optical material snapshot, with field-level
 * inheritance. Updates publish atomically; GL threads never read preferences.
 */
public final class GlassEdgeEffects {
    public static final String GLOBAL = "global";
    public static final String DOCK = "dock";
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
            DOCK, ICON, WIDGET, FOLDER, POPUP, HANDLE_MENU, DIALOG, GBOARD,
            SEARCHBOX, SECURITY_CENTER));
    public static final List<ConfigKey<?>> KEYS = Collections.unmodifiableList(Arrays.asList(
            ConfigSchema.Glass.BACKLIT_ENABLED,
            ConfigSchema.Glass.BACKLIT_WIDTH,
            ConfigSchema.Glass.BACKLIT_STRENGTH,
            ConfigSchema.Glass.BACKLIT_SOFTNESS,
            ConfigSchema.Glass.BACKLIT_EXTENT,
            ConfigSchema.Glass.OUTER_SHADOW_ENABLED,
            ConfigSchema.Glass.OUTER_SHADOW_RADIUS,
            ConfigSchema.Glass.OUTER_SHADOW_OFFSET_Y,
            ConfigSchema.Glass.OUTER_SHADOW_RED,
            ConfigSchema.Glass.OUTER_SHADOW_GREEN,
            ConfigSchema.Glass.OUTER_SHADOW_BLUE,
            ConfigSchema.Glass.OUTER_SHADOW_ALPHA,
            ConfigSchema.Glass.OUTER_SHADOW_LIGHT_FOLLOW));

    private static volatile Map<String, PrismalEdgeDecoration> current =
            Collections.emptyMap();
    private static volatile Map<String, PrismalEdgeDecoration> withoutNativeShadows =
            Collections.emptyMap();

    private GlassEdgeEffects() {}

    public static String key(String scope, ConfigKey<?> config) {
        if (!SCOPES.contains(scope) || !KEYS.contains(config)) {
            throw new IllegalArgumentException("unsupported optical material field: " + scope);
        }
        return "liquid_scope_" + scope + "_"
                + config.name().substring("liquid_".length());
    }

    static PrismalEdgeDecoration resolve(ConfigReader reader, String scope) {
        if (!GLOBAL.equals(scope) && !SCOPES.contains(scope)) {
            throw new IllegalArgumentException("unsupported optical material scope: " + scope);
        }
        if (reader == null) return PrismalEdgeDecoration.NONE;
        boolean backlit = bool(reader, scope, ConfigSchema.Glass.BACKLIT_ENABLED);
        boolean castShadow = bool(reader, scope, ConfigSchema.Glass.OUTER_SHADOW_ENABLED);
        return new PrismalEdgeDecoration(
                backlit ? decimal(reader, scope, ConfigSchema.Glass.BACKLIT_WIDTH) : 0f,
                backlit ? integer(reader, scope, ConfigSchema.Glass.BACKLIT_STRENGTH) / 100f : 0f,
                integer(reader, scope, ConfigSchema.Glass.BACKLIT_SOFTNESS) / 100f,
                castShadow ? decimal(reader, scope, ConfigSchema.Glass.OUTER_SHADOW_RADIUS) : 0f,
                decimal(reader, scope, ConfigSchema.Glass.OUTER_SHADOW_OFFSET_Y),
                integer(reader, scope, ConfigSchema.Glass.OUTER_SHADOW_RED) / 255f,
                integer(reader, scope, ConfigSchema.Glass.OUTER_SHADOW_GREEN) / 255f,
                integer(reader, scope, ConfigSchema.Glass.OUTER_SHADOW_BLUE) / 255f,
                castShadow ? integer(reader, scope, ConfigSchema.Glass.OUTER_SHADOW_ALPHA) / 255f : 0f,
                integer(reader, scope, ConfigSchema.Glass.OUTER_SHADOW_LIGHT_FOLLOW) / 100f,
                integer(reader, scope, ConfigSchema.Glass.BACKLIT_EXTENT) / 100f);
    }

    public static synchronized void refresh(ConfigReader reader) {
        Map<String, PrismalEdgeDecoration> active = new HashMap<>();
        Map<String, PrismalEdgeDecoration> onlyOptics = new HashMap<>();
        active.put(GLOBAL, resolve(reader, GLOBAL));
        onlyOptics.put(GLOBAL, active.get(GLOBAL).withoutCastShadow());
        for (String scope : SCOPES) {
            PrismalEdgeDecoration material = resolve(reader, scope);
            active.put(scope, material);
            PrismalEdgeDecoration optics = material.withoutCastShadow();
            if (DOCK.equals(scope)) {
                // Dock's native foreground and MiShadow use the full Host
                // geometry. Do not shrink its glass; fit only the ink into the
                // first visible TextureView AA pixel. The wider old 1.5px
                // shift placed subpixel ink outside the sampled coverage.
                optics = optics.fitInkToNativeViewport(1f);
            }
            onlyOptics.put(scope, optics);
        }
        current = Collections.unmodifiableMap(active);
        withoutNativeShadows = Collections.unmodifiableMap(onlyOptics);
    }

    public static PrismalEdgeDecoration current(String scope) {
        PrismalEdgeDecoration material = current.get(scope);
        return material == null ? PrismalEdgeDecoration.NONE : material;
    }

    /** Never render a duplicate exterior shadow over an OEM native shadow. */
    public static PrismalEdgeDecoration withoutNativeShadow(String scope) {
        PrismalEdgeDecoration material = withoutNativeShadows.get(scope);
        return material == null ? PrismalEdgeDecoration.NONE : material;
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
