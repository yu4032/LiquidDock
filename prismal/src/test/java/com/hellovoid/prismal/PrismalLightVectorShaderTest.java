package com.hellovoid.prismal;

import static org.junit.Assert.*;
import org.junit.Test;

/** Verified shader-source contract and uniform-only vector equivalence. */
public class PrismalLightVectorShaderTest {
    @Test public void replacesOnlyConstantPerPixelLightExpressions() {
        String original = PrismalShaderSources.FRAGMENT;
        String patched = PrismalLightVectorShader.apply(original);
        assertTrue(patched.contains("uniform vec3 u_halfSecondary;"));
        assertTrue(patched.contains("vec3 Lp = u_lightPrimary;"));
        assertTrue(patched.contains("vec3 Ls = u_lightSecondary;"));
        assertTrue(patched.contains("vec3 Hp = u_halfPrimary;"));
        assertTrue(patched.contains("vec3 Hs = u_halfSecondary;"));
        assertFalse(patched.contains("vec3 Lp = normalize(vec3(u_lightDir, 1.45));"));
        assertTrue(patched.contains("float specP = pow(max(dot(N, Hp), 0.0), sh)"));
        // The same upstream shader must still be used; no dropped refraction or OS4 edge.
        assertTrue(patched.contains("vec3 refIn = refract(-V, N, 1.0 / u_ior);"));
        assertTrue(patched.contains("float causticDot = dot(normalize(vec3(gradH * u_normalStrength, 0.45)), Lp);"));
    }

    @Test public void refusesUnexpectedUpstreamShaderChanges() {
        assertThrows(IllegalStateException.class,
                () -> PrismalLightVectorShader.apply("uniform vec2  u_lightDir;\nvoid main(){}"));
    }

    @Test public void matchesGpuNormalizationAtWideDirectionalAngles() {
        for (float x : new float[]{-4f, -0.5f, 0f, 0.61f, 3f}) {
            for (float y : new float[]{-3.2f, -0.8f, 0f, 0.75f, 2.9f}) {
                float[] v = new float[12];
                PrismalLightVectorShader.compute(x, y, v);
                assertNormalized(v, 0);
                assertNormalized(v, 3);
                assertNormalized(v, 6);
                assertNormalized(v, 9);
                assertVector(normal(x, y, 1.45), v, 0);
                assertVector(normal(-x * 0.62 + 0.41, -y * 0.62 + 0.33, 0.74), v, 3);
                assertVector(normal(v[0], v[1], v[2] + 1), v, 6);
                assertVector(normal(v[3], v[4], v[5] + 1), v, 9);
            }
        }
    }

    private static void assertNormalized(float[] v, int start) {
        double length = Math.sqrt(v[start]*v[start]
                + v[start+1]*v[start+1] + v[start+2]*v[start+2]);
        assertEquals(1.0, length, 0.000001);
    }

    private static double[] normal(double x, double y, double z) {
        double len = Math.sqrt(x*x + y*y + z*z);
        return new double[]{x/len, y/len, z/len};
    }

    private static void assertVector(double[] want, float[] actual, int start) {
        for (int i = 0; i < 3; i++) assertEquals(want[i], actual[start+i], 0.000001);
    }
}
