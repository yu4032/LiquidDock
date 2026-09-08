package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.hellovoid.prismal.PrismalParams;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/** User-selected OS4 edge values must reach the portable renderer in logical-pixel units. */
public class Os4EdgeConfigRuntimeTest {
    @Test
    public void configuredValuesReachEveryPortablePrismalNodeWithoutUnitDrift() {
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

        assertEquals(36f, floatField(portable, "os4EdgeWidthPx"), 0.0001f);
        assertEquals(18f, floatField(portable, "os4ReflectOffsetPx"), 0.0001f);
        assertEquals(0.75f, floatField(portable, "os4ReflectionStrength"), 0.0001f);
        assertEquals(0.25f, floatField(portable, "os4ReflectionLighten"), 0.0001f);
        assertEquals(0.80f, floatField(portable, "os4DirectionalAngleRange"), 0.0001f);
        assertEquals(1.10f, floatField(portable, "os4DirectionalIntensity"), 0.0001f);
        assertEquals(0.35f, floatField(portable, "os4DirectionalOppositeIntensity"), 0.0001f);
    }

    private static float floatField(Object target, String name) {
        try {
            Field field = target.getClass().getField(name);
            return field.getFloat(target);
        } catch (ReflectiveOperationException e) {
            fail("Missing OS4 runtime control: " + name);
            return Float.NaN;
        }
    }
}
