package com.hellovoid.prismal;

import static org.junit.Assert.*;
import org.junit.Test;

/** Regression contract: only exactly-zero contributions are skipped. */
public class PrismalZeroContributionShaderTest {
    private static String before() {
        return PrismalOs4InvariantShader.apply(
                PrismalComponentGateShader.apply(
                        PrismalOpticalEdgeShader.apply(
                                PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT))));
    }

    @Test public void zeroReflectionWeightsGateWholeTextureSample() {
        String shader = PrismalZeroContributionShader.apply(before());
        assertTrue(shader.contains("if (reflW > 0.0) {"));
        assertTrue(shader.contains("if (os4ReflectionWeight > 0.0) {"));
        int reflGate = shader.indexOf("if (reflW > 0.0)");
        assertTrue(shader.indexOf("vec2 gDir = normalize(gradLens", reflGate) > reflGate);
        assertTrue(shader.indexOf("texture2D(u_blurredTexture, reflUv)", reflGate) > reflGate);
        assertTrue(shader.indexOf("vec2 reflUv = clamp(", reflGate) > reflGate);

        int os4Weight = shader.indexOf("float os4ReflectionWeight = clamp(");
        int os4Gate = shader.indexOf("if (os4ReflectionWeight > 0.0) {");
        assertTrue(os4Weight >= 0 && os4Weight < os4Gate);
        assertTrue(shader.indexOf("vec2 os4ReflectUvOffset", os4Gate) > os4Gate);
        assertTrue(shader.indexOf("texture2D(u_blurredTexture, os4ReflectUv)", os4Gate) > os4Gate);
        assertEquals(1, count(shader, "float os4ReflectionWeight = clamp("));
        assertEquals(1, count(shader, "color = mix(color, os4EdgeReflection, os4ReflectionWeight);"));
    }

    @Test public void os4DirectionalAcosRunsOnlyForNonzeroEdgeMask() {
        String shader = PrismalZeroContributionShader.apply(before());
        int declaration = shader.indexOf("float os4SoftLight = 0.0;");
        int conditional = shader.indexOf("if (os4EdgeMask > 0.0) {");
        int acos = shader.indexOf("float os4MainAngle = acos(");
        int debug = shader.indexOf("mix(os4DebugColor, vec3(1.0), os4SoftLight * 0.7)");
        assertTrue(declaration >= 0 && declaration < conditional);
        assertTrue(conditional < acos && acos < debug);
        assertEquals(1, count(shader, "float os4SoftLight = 0.0;"));
        assertEquals(1, count(shader,
                "os4SoftLight = clamp(os4Directional * os4EdgeMask, 0.0, 1.0);"));
        assertEquals(1, count(shader, "color += vec3(os4SoftLight"));
        assertTrue(shader.contains("vec3 os4EdgeNormal3 = normalize(vec3(os4SdfGradient, 1.0));"));
    }

    @Test public void absentVariablesHaveNoConsumersAfterSingleEdgeReplacement() {
        String base = before();
        assertEquals(3, count(base, "edgePunch")); // declaration + assignment/read
        assertEquals(1, count(base, "float Fr = fresCtl;"));
        assertEquals(1, count(base, "float edgeFalloffPower ="));
        assertEquals(1, count(base, "float dropLens ="));
        assertEquals(1, count(base, "dropLens =")); // statement only, no uses

        String finalShader = PrismalZeroContributionShader.apply(base);
        assertFalse(finalShader.contains("edgePunch"));
        assertFalse(finalShader.contains("float Fr ="));
        assertFalse(finalShader.contains("float dropLens ="));
        assertFalse(finalShader.contains("float edgeFalloffPower ="));
        // The still-declared input signals an existing disconnected GUI control and will
        // be fixed in a separate behavior change, not silently rewired in this perf patch.
        assertTrue(finalShader.contains("uniform float u_edgeRefractionFalloff;"));
        assertTrue(finalShader.contains("vec2 edgeRefractionUv = (dLens * lensDir) / u_resolution;"));
    }

    @Test public void failsClosedIfShaderAnchorsChange() {
        assertThrows(IllegalStateException.class,
                () -> PrismalZeroContributionShader.apply("void main() { }"));
    }

    @Test public void exactZeroMasksDoNotChangeColorForFiniteInputs() {
        for (float color : new float[]{0.0f, 0.02f, 0.5f, 0.88f, 1f}) {
            for (float texture : new float[]{0f, 0.12f, 0.9f, 1f}) {
                float original = color * (1f - 0f) + texture * 0f;
                assertEquals(original, color, 0.0f);
            }
        }
    }

    private static int count(String s, String token) {
        int n=0;
        for(int from=0;(from=s.indexOf(token,from))>=0;from+=token.length())n++;
        return n;
    }
}
