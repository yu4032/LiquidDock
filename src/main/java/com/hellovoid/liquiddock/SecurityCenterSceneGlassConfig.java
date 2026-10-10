package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigKey;
import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.prismal.PrismalParams;

/** Immutable per-scene output choices. All scenes share the existing root PassBlur producer. */
final class SecurityCenterSceneGlassConfig {
    enum Scene { DOCK, ALL_APPS, GAME_TOOLBOX, VIDEO_TOOLBOX }

    static final class Style {
        final boolean enabled;
        final int blur, red, green, blue, alpha;

        Style(boolean enabled, int blur, int red, int green, int blue, int alpha) {
            this.enabled = enabled;
            this.blur = blur;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
        }

        PrismalParams apply(PrismalParams inherited) {
            if (blur < 0 && red < 0 && green < 0 && blue < 0 && alpha < 0) {
                return inherited;
            }
            PrismalParams.Builder out = PrismalParams.builder(inherited);
            if (blur >= 0) out.blurRadiusPx = blur;
            if (red >= 0) out.tintR = red / 255f;
            if (green >= 0) out.tintG = green / 255f;
            if (blue >= 0) out.tintB = blue / 255f;
            if (alpha >= 0) out.tintA = alpha / 255f;
            return out.build();
        }
    }

    private final Style dock, apps, game, video;

    private SecurityCenterSceneGlassConfig(Style dock, Style apps, Style game, Style video) {
        this.dock = dock;
        this.apps = apps;
        this.game = game;
        this.video = video;
    }

    static SecurityCenterSceneGlassConfig read(ConfigReader c) {
        return new SecurityCenterSceneGlassConfig(
                readStyle(c, ConfigSchema.SecurityCenterScene.DOCK_ENABLED,
                        ConfigSchema.SecurityCenterScene.DOCK_BLUR,
                        ConfigSchema.SecurityCenterScene.DOCK_TINT_RED,
                        ConfigSchema.SecurityCenterScene.DOCK_TINT_GREEN,
                        ConfigSchema.SecurityCenterScene.DOCK_TINT_BLUE,
                        ConfigSchema.SecurityCenterScene.DOCK_TINT_ALPHA),
                readStyle(c, ConfigSchema.SecurityCenterScene.ALL_APPS_ENABLED,
                        ConfigSchema.SecurityCenterScene.ALL_APPS_BLUR,
                        ConfigSchema.SecurityCenterScene.ALL_APPS_TINT_RED,
                        ConfigSchema.SecurityCenterScene.ALL_APPS_TINT_GREEN,
                        ConfigSchema.SecurityCenterScene.ALL_APPS_TINT_BLUE,
                        ConfigSchema.SecurityCenterScene.ALL_APPS_TINT_ALPHA),
                readStyle(c, ConfigSchema.SecurityCenterScene.GAME_TOOLBOX_ENABLED,
                        ConfigSchema.SecurityCenterScene.GAME_TOOLBOX_BLUR,
                        ConfigSchema.SecurityCenterScene.GAME_TOOLBOX_TINT_RED,
                        ConfigSchema.SecurityCenterScene.GAME_TOOLBOX_TINT_GREEN,
                        ConfigSchema.SecurityCenterScene.GAME_TOOLBOX_TINT_BLUE,
                        ConfigSchema.SecurityCenterScene.GAME_TOOLBOX_TINT_ALPHA),
                readStyle(c, ConfigSchema.SecurityCenterScene.VIDEO_TOOLBOX_ENABLED,
                        ConfigSchema.SecurityCenterScene.VIDEO_TOOLBOX_BLUR,
                        ConfigSchema.SecurityCenterScene.VIDEO_TOOLBOX_TINT_RED,
                        ConfigSchema.SecurityCenterScene.VIDEO_TOOLBOX_TINT_GREEN,
                        ConfigSchema.SecurityCenterScene.VIDEO_TOOLBOX_TINT_BLUE,
                        ConfigSchema.SecurityCenterScene.VIDEO_TOOLBOX_TINT_ALPHA));
    }

    private static Style readStyle(ConfigReader reader, ConfigKey<Boolean> enabled,
            ConfigKey<Integer> blur, ConfigKey<Integer> red, ConfigKey<Integer> green,
            ConfigKey<Integer> blue, ConfigKey<Integer> alpha) {
        return new Style(reader.b(enabled.name(), enabled.runtimeFallback()),
                readChannel(reader, blur), readChannel(reader, red),
                readChannel(reader, green), readChannel(reader, blue),
                readChannel(reader, alpha));
    }

    private static int readChannel(ConfigReader reader, ConfigKey<Integer> key) {
        int value = reader.i(key.name(), key.runtimeFallback());
        return Math.max(key.minInt(), Math.min(key.maxInt(), value));
    }

    Style style(Scene scene) {
        switch (scene) {
            case ALL_APPS: return apps;
            case GAME_TOOLBOX: return game;
            case VIDEO_TOOLBOX: return video;
            default: return dock;
        }
    }

    static Scene forOutput(SecurityCenterSinkOutputPolicy.MaterialRole role, int assistantType) {
        if (role == SecurityCenterSinkOutputPolicy.MaterialRole.ALL_APPS) return Scene.ALL_APPS;
        if (role == SecurityCenterSinkOutputPolicy.MaterialRole.TOOLBOX) {
            return assistantType == 1 ? Scene.GAME_TOOLBOX : Scene.VIDEO_TOOLBOX;
        }
        return Scene.DOCK;
    }

    boolean canPresent(int assistantType) {
        return dock.enabled || (assistantType == 4
                ? apps.enabled : assistantType == 1 ? game.enabled : video.enabled);
    }

    boolean sameEnabled(SecurityCenterSceneGlassConfig other) {
        return other != null && dock.enabled == other.dock.enabled
                && apps.enabled == other.apps.enabled
                && game.enabled == other.game.enabled && video.enabled == other.video.enabled;
    }
}
