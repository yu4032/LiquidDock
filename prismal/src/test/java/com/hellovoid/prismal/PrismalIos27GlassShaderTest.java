package com.hellovoid.prismal;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class PrismalIos27GlassShaderTest {
    private static String compiledFragment() {
        return PrismalIos27GlassShader.apply(
                PrismalOs4InvariantShader.apply(
                    PrismalComponentGateShader.apply(
                        PrismalOpticalEdgeShader.apply(
                            PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT)))));
    }

    @Test public void sameSdfProvidesReflectionAndDarkContourForEveryGlass() {
        String s = compiledFragment();
        assertTrue(s.contains("if (opacity < 0.001 && distMask > 1.7) discard;"));
        assertTrue(s.contains("vec2 iosNormal = normalize(os4SdfGradient"));
        assertTrue(s.contains("float iosFacing = dot(iosNormal, u_invariantLightXY)"));
        assertTrue(s.contains("iosOuterRamp * iosOuterTail"));
        assertTrue(s.contains("iosAlpha = iosCombined"));
        assertTrue(s.contains("gl_FragColor = vec4(color, iosAlpha);"));
        assertFalse(s.contains("u_ios27DockEnabled"));
        assertFalse(s.contains("gl_FragColor = vec4(color, opacity * u_transmittance);"));
    }

    @Test public void keepsNativeOneEdgeLensAndScaleForSmallGlass() {
        String s = compiledFragment();
        assertTrue(s.contains("vec2 edgeRefractionUv = (dLens * lensDir) / u_resolution;"));
        assertTrue(s.contains("float iosLensSize = smoothstep(22.0, 72.0, minDim);"));
        assertTrue(s.contains("float iosSize = clamp(min(u_glassSize.x, u_glassSize.y) / 140.0"));
        assertTrue(s.contains("if (edgeDist < 12.0 * iosSize && distMask < 1.7)"));
        assertFalse(s.contains("uniform float u_ios27GlassEnabled"));
    }

    @Test public void rendererAppliesSharedMaterialAtShaderBuildNotPerProfile() throws Exception {
        String r = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/hellovoid/prismal/PrismalRenderer.java"));
        assertTrue(r.contains("PrismalIos27GlassShader.apply(glassFragment)"));
        assertFalse(r.contains("u_ios27DockEnabled"));
        assertFalse(r.contains("withIos27DockOptics"));
    }
}
