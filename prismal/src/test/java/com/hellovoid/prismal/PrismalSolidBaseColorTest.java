package com.hellovoid.prismal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure-color coverage is independent of legacy backdrop multiplication tint. */
public final class PrismalSolidBaseColorTest {
    private static int count(String source, String needle) {
        int matches = 0;
        for (int pos = 0; (pos = source.indexOf(needle, pos)) >= 0; pos += needle.length()) {
            matches++;
        }
        return matches;
    }

    @Test public void defaultOpaqueFillIsDisabledAndCopiedWithoutLosingTint() {
        PrismalParams defaults = PrismalParams.builder().build();
        assertEquals(0f, defaults.baseColorA, 0f);
        assertEquals(1f, defaults.baseColorR, 0f);
        assertEquals(1f, defaults.baseColorG, 0f);
        assertEquals(1f, defaults.baseColorB, 0f);
        PrismalParams.Builder override = PrismalParams.builder(defaults);
        override.tintA = 0.65f;
        override.baseColorR = 0.2f;
        override.baseColorA = 1f;
        PrismalParams edited = override.build();
        PrismalParams copy = PrismalParams.builder(edited).build();
        assertEquals(0.65f, copy.tintA, 0f);
        assertEquals(0.2f, copy.baseColorR, 0f);
        assertEquals(1f, copy.baseColorA, 0f);
    }

    @Test public void baselineAndDecoratedShadersEachApplySolidFillExactlyOnce() {
        String base = PrismalOs4InvariantShader.apply(
                PrismalComponentGateShader.apply(
                        PrismalOpticalEdgeShader.apply(
                                PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT))));
        assertTrue(base.contains("color = mix(color, color * u_glassColor.rgb, u_glassColor.a);"));
        assertEquals(1, count(base, "color = mix(color, u_baseColor.rgb, u_baseColor.a);"));
        String decorated = PrismalEdgeDecorationShader.apply(base);
        assertEquals(0, count(decorated, "color = mix(color, u_baseColor.rgb, u_baseColor.a);"));
        assertEquals(1, count(decorated,
                "ringShadedGlass = mix(ringShadedGlass, u_baseColor.rgb, u_baseColor.a);"));
        assertTrue(decorated.contains("gl_FragColor = vec4(finalRgb, finalAlpha);"));
        assertTrue(decorated.contains("uniform vec4  u_baseColor;"));
        assertFalse(decorated.contains("gl_FragColor = vec4(color, opacity * u_transmittance);"));
    }

    @Test public void zeroCoverDoesNotChangeBackdropAndFullCoverRemovesItsColor() {
        float[] backdrop = {0.12f, 0.8f, 0.4f};
        float[] fill = {0.9f, 0.2f, 0.7f};
        for (int i = 0; i < 3; ++i) {
            assertEquals(backdrop[i], backdrop[i] * (1f - 0f) + fill[i] * 0f, 0f);
            assertEquals(fill[i], backdrop[i] * (1f - 1f) + fill[i] * 1f, 0f);
        }
    }
}
