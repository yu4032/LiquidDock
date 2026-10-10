package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigKey;
import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.prismal.PrismalParams;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class SecurityCenterSceneGlassConfigTest {
    @Test public void absentKeysKeepAllExistingScenesEnabledAndGlobalAppearance() {
        SecurityCenterSceneGlassConfig config =
                SecurityCenterSceneGlassConfig.read(new ConfigReader(Map.of()));
        PrismalParams.Builder base = PrismalParams.builder();
        base.blurRadiusPx = 12.5f;
        base.tintR = 0.25f;
        base.tintG = 0.5f;
        base.tintB = 0.75f;
        base.tintA = 0.3f;
        PrismalParams inherited = base.build();
        for (SecurityCenterSceneGlassConfig.Scene scene :
                SecurityCenterSceneGlassConfig.Scene.values()) {
            assertTrue(config.style(scene).enabled);
            assertSame(inherited, config.style(scene).apply(inherited));
        }
        assertTrue(config.canPresent(1));
        assertTrue(config.canPresent(3));
        assertTrue(config.canPresent(4));
    }

    @Test public void sceneOverridesNeverMutateGlobalOrAnotherScene() {
        Map<String, Object> map = new HashMap<>();
        map.put(ConfigSchema.SecurityCenterScene.ALL_APPS_BLUR.name(), 27);
        map.put(ConfigSchema.SecurityCenterScene.ALL_APPS_TINT_RED.name(), 205);
        map.put(ConfigSchema.SecurityCenterScene.ALL_APPS_TINT_GREEN.name(), 31);
        map.put(ConfigSchema.SecurityCenterScene.ALL_APPS_TINT_BLUE.name(), 90);
        map.put(ConfigSchema.SecurityCenterScene.ALL_APPS_TINT_ALPHA.name(), 128);
        SecurityCenterSceneGlassConfig config =
                SecurityCenterSceneGlassConfig.read(new ConfigReader(map));
        PrismalParams.Builder b = PrismalParams.builder();
        b.blurRadiusPx = 6.5f;
        b.tintA = 0.4f;
        PrismalParams inherited = b.build();
        PrismalParams apps = config.style(SecurityCenterSceneGlassConfig.Scene.ALL_APPS)
                .apply(inherited);
        assertEquals(27f, apps.blurRadiusPx, 0.0001f);
        assertEquals(205f / 255f, apps.tintR, 0.0001f);
        assertEquals(31f / 255f, apps.tintG, 0.0001f);
        assertEquals(90f / 255f, apps.tintB, 0.0001f);
        assertEquals(128f / 255f, apps.tintA, 0.0001f);
        assertEquals(6.5f, inherited.blurRadiusPx, 0.0001f);
        assertSame(inherited,
                config.style(SecurityCenterSceneGlassConfig.Scene.DOCK).apply(inherited));
        assertSame(inherited,
                config.style(SecurityCenterSceneGlassConfig.Scene.GAME_TOOLBOX).apply(inherited));
    }

    @Test public void sceneSelectionAndIndependentEnableGates() {
        assertEquals(SecurityCenterSceneGlassConfig.Scene.DOCK,
                SecurityCenterSceneGlassConfig.forOutput(
                        SecurityCenterSinkOutputPolicy.MaterialRole.DOCK_PREVIEW, 4));
        assertEquals(SecurityCenterSceneGlassConfig.Scene.ALL_APPS,
                SecurityCenterSceneGlassConfig.forOutput(
                        SecurityCenterSinkOutputPolicy.MaterialRole.ALL_APPS, 4));
        assertEquals(SecurityCenterSceneGlassConfig.Scene.GAME_TOOLBOX,
                SecurityCenterSceneGlassConfig.forOutput(
                        SecurityCenterSinkOutputPolicy.MaterialRole.TOOLBOX, 1));
        assertEquals(SecurityCenterSceneGlassConfig.Scene.VIDEO_TOOLBOX,
                SecurityCenterSceneGlassConfig.forOutput(
                        SecurityCenterSinkOutputPolicy.MaterialRole.TOOLBOX, 3));
        Map<String, Object> opts = new HashMap<>();
        opts.put(ConfigSchema.SecurityCenterScene.DOCK_ENABLED.name(), false);
        opts.put(ConfigSchema.SecurityCenterScene.GAME_TOOLBOX_ENABLED.name(), false);
        opts.put(ConfigSchema.SecurityCenterScene.VIDEO_TOOLBOX_ENABLED.name(), false);
        SecurityCenterSceneGlassConfig config =
                SecurityCenterSceneGlassConfig.read(new ConfigReader(opts));
        assertTrue("All Apps stays independent of Dock", config.canPresent(4));
        assertFalse(config.canPresent(1));
        assertFalse(config.canPresent(3));
        opts.put(ConfigSchema.SecurityCenterScene.ALL_APPS_ENABLED.name(), false);
        assertFalse(config.sameEnabled(SecurityCenterSceneGlassConfig.read(new ConfigReader(opts))));
    }

    @Test public void channelsClampAndAllKeysAreRegisteredForJson() {
        Map<String, Object> opts = Map.of(
                ConfigSchema.SecurityCenterScene.GAME_TOOLBOX_BLUR.name(), 999,
                ConfigSchema.SecurityCenterScene.GAME_TOOLBOX_TINT_ALPHA.name(), -100);
        SecurityCenterSceneGlassConfig config =
                SecurityCenterSceneGlassConfig.read(new ConfigReader(opts));
        SecurityCenterSceneGlassConfig.Style style =
                config.style(SecurityCenterSceneGlassConfig.Scene.GAME_TOOLBOX);
        assertEquals(60, style.blur);
        assertEquals(-1, style.alpha);
        ConfigKey<?>[] keys = {
                ConfigSchema.SecurityCenterScene.DOCK_ENABLED,
                ConfigSchema.SecurityCenterScene.DOCK_BLUR,
                ConfigSchema.SecurityCenterScene.ALL_APPS_ENABLED,
                ConfigSchema.SecurityCenterScene.ALL_APPS_TINT_BLUE,
                ConfigSchema.SecurityCenterScene.GAME_TOOLBOX_ENABLED,
                ConfigSchema.SecurityCenterScene.GAME_TOOLBOX_TINT_ALPHA,
                ConfigSchema.SecurityCenterScene.VIDEO_TOOLBOX_ENABLED,
                ConfigSchema.SecurityCenterScene.VIDEO_TOOLBOX_TINT_RED
        };
        for (ConfigKey<?> key : keys) assertTrue(ConfigSchema.all().contains(key));
    }
}
