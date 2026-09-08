package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.ConfigKey;
import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.prismal.PrismalParams;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/** Runtime contract for the user-facing OS4 edge controls. */
public class Os4EdgeConfigRuntimeTest {
    @Test
    public void schemaUsesApprovedGuiDefaultsAndRanges() {
        assertKey(ConfigSchema.Glass.OS4_EDGE_WIDTH_PX,
                "liquid_os4_edge_width_px", 20, 4, 64);
        assertKey(ConfigSchema.Glass.OS4_REFLECT_OFFSET_PX,
                "liquid_os4_reflect_offset_px", 10, 0, 40);
        assertKey(ConfigSchema.Glass.OS4_REFLECTION_STRENGTH,
                "liquid_os4_reflection_strength", 28, 0, 200);
        assertKey(ConfigSchema.Glass.OS4_REFLECTION_LIGHTEN,
                "liquid_os4_reflection_lighten", 16, 0, 100);
        assertKey(ConfigSchema.Glass.OS4_DIRECTIONAL_ANGLE_RANGE,
                "liquid_os4_directional_angle_range", 52, 5, 150);
        assertKey(ConfigSchema.Glass.OS4_DIRECTIONAL_INTENSITY,
                "liquid_os4_directional_intensity", 42, 0, 200);
        assertKey(ConfigSchema.Glass.OS4_DIRECTIONAL_OPPOSITE_INTENSITY,
                "liquid_os4_directional_opposite_intensity", 14, 0, 200);
    }

    @Test
    public void configuredValuesReachPortablePrismalWithoutUnitDrift() {
        Map<String, Object> values = new HashMap<>();
        values.put("liquid_os4_edge_width_px", 36);
        values.put("liquid_os4_reflect_offset_px", 18);
        values.put("liquid_os4_reflection_strength", 75);
        values.put("liquid_os4_reflection_lighten", 25);
        values.put("liquid_os4_directional_angle_range", 80);
        values.put("liquid_os4_directional_intensity", 110);
        values.put("liquid_os4_directional_opposite_intensity", 35);

        LiquidDockConfig config = LiquidDockConfig.from(new ConfigReader(values));
        Miuix307PrismalMaterial.Params material =
                Miuix307PrismalMaterial.fromConfig(config.glass, 3f);
        PrismalParams portable = Miuix307PrismalAdapter.toPortable(material);

        assertEquals(36f, portable.os4EdgeWidthPx, 0.0001f);
        assertEquals(18f, portable.os4ReflectOffsetPx, 0.0001f);
        assertEquals(0.75f, portable.os4ReflectionStrength, 0.0001f);
        assertEquals(0.25f, portable.os4ReflectionLighten, 0.0001f);
        assertEquals(0.80f, portable.os4DirectionalAngleRange, 0.0001f);
        assertEquals(1.10f, portable.os4DirectionalIntensity, 0.0001f);
        assertEquals(0.35f, portable.os4DirectionalOppositeIntensity, 0.0001f);
    }

    @Test
    public void untouchedConfigUsesTheApprovedThickerEdgeDefaults() {
        LiquidDockConfig config = LiquidDockConfig.from(new ConfigReader(Map.of()));
        PrismalParams portable = Miuix307PrismalAdapter.toPortable(
                Miuix307PrismalMaterial.fromConfig(config.glass, 2f));

        assertEquals(20f, portable.os4EdgeWidthPx, 0.0001f);
        assertEquals(10f, portable.os4ReflectOffsetPx, 0.0001f);
        assertEquals(0.28f, portable.os4ReflectionStrength, 0.0001f);
        assertEquals(0.16f, portable.os4ReflectionLighten, 0.0001f);
        assertEquals(0.52f, portable.os4DirectionalAngleRange, 0.0001f);
        assertEquals(0.42f, portable.os4DirectionalIntensity, 0.0001f);
        assertEquals(0.14f, portable.os4DirectionalOppositeIntensity, 0.0001f);
    }

    @Test
    public void widerOs4EdgeAndReflectionExpandSamplingGuard() {
        LiquidDockConfig baseConfig = LiquidDockConfig.from(new ConfigReader(Map.of()));
        Miuix307PrismalMaterial.Params base =
                Miuix307PrismalMaterial.fromConfig(baseConfig.glass, 2f);

        Map<String, Object> values = new HashMap<>();
        values.put("liquid_os4_edge_width_px", 64);
        values.put("liquid_os4_reflect_offset_px", 40);
        LiquidDockConfig wideConfig = LiquidDockConfig.from(new ConfigReader(values));
        Miuix307PrismalMaterial.Params wide =
                Miuix307PrismalMaterial.fromConfig(wideConfig.glass, 2f);

        assertTrue(Miuix307PrismalMaterial.requiredSampleGuardPx(wide, 900, 220, true)
                > Miuix307PrismalMaterial.requiredSampleGuardPx(base, 900, 220, true));
    }

    private static void assertKey(
            ConfigKey<Integer> key, String name, int def, int min, int max) {
        assertEquals(name, key.name());
        assertEquals(Integer.valueOf(def), key.uiDefault());
        assertEquals(Integer.valueOf(def), key.runtimeFallback());
        assertEquals(Integer.valueOf(def), key.exportDefault());
        assertEquals(Integer.valueOf(min), key.minInt());
        assertEquals(Integer.valueOf(max), key.maxInt());
        assertEquals(ConfigKey.StorageMode.DIRECT, key.storageMode());
        assertEquals(ConfigKey.ExportMode.ALWAYS, key.exportMode());
    }
}
