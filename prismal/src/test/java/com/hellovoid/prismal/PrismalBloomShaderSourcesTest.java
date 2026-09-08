package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrismalBloomShaderSourcesTest {
    @Test
    public void maskBuildsFiniteAreaRingWithoutBackdropSampling() {
        String shader = PrismalBloomShaderSources.MASK_FRAGMENT;

        assertTrue(shader.contains("uniform vec2 u_targetSize;"));
        assertTrue(shader.contains("uniform vec2 u_glassSize;"));
        assertTrue(shader.contains("uniform vec4 u_cornerRadii;"));
        assertTrue(shader.contains("uniform float u_bloomWidthPx;"));
        assertTrue(shader.contains("uniform float u_bloomIntensity;"));
        assertTrue(shader.contains("uniform vec2 u_lightDir;"));
        assertTrue(shader.contains("float outer = 1.0 - smoothstep(-aa, aa, sd - halfWidth);"));
        assertTrue(shader.contains("float inner = 1.0 - smoothstep(-aa, aa, sd + halfWidth);"));
        assertTrue(shader.contains("float ring = clamp(outer - inner, 0.0, 1.0);"));
        assertFalse(shader.contains("sampler2D"));
        assertFalse(shader.contains("u_backgroundTexture"));
        assertFalse(shader.contains("u_blurredTexture"));
    }

    @Test
    public void maskDerivesSdfNormalAndDirectionalMainOppositeAmbientLighting() {
        String shader = PrismalBloomShaderSources.MASK_FRAGMENT;

        assertTrue(shader.contains("float sdfXp = bloomSd(pPx + vec2(1.0, 0.0)"));
        assertTrue(shader.contains("float sdfXn = bloomSd(pPx - vec2(1.0, 0.0)"));
        assertTrue(shader.contains("float sdfYp = bloomSd(pPx + vec2(0.0, 1.0)"));
        assertTrue(shader.contains("float sdfYn = bloomSd(pPx - vec2(0.0, 1.0)"));
        assertTrue(shader.contains("vec2 edgeNormal = normalize(vec2(sdfXp - sdfXn, sdfYp - sdfYn));"));
        assertTrue(shader.contains("float mainLight = pow(max(facing, 0.0), 1.45) * 0.95;"));
        assertTrue(shader.contains("float oppositeLight = pow(max(-facing, 0.0), 1.10) * 0.34;"));
        assertTrue(shader.contains("float ambientLight = 0.16;"));
        assertTrue(shader.contains("gl_FragColor = vec4(bloomRgb * alpha, alpha);"));
    }

    @Test
    public void maskKeepsPerCornerRoundedRectangleGeometry() {
        String shader = PrismalBloomShaderSources.MASK_FRAGMENT;

        assertTrue(shader.contains("uniform vec4 u_cornerRadii;"));
        assertTrue(shader.contains("float radiusAtCentered(vec2 c, vec4 radii)"));
        assertTrue(shader.contains("return radii.x;"));
        assertTrue(shader.contains("return radii.y;"));
        assertTrue(shader.contains("return radii.z;"));
        assertTrue(shader.contains("return radii.w;"));
        assertTrue(shader.contains("bloomSd(pPx, halfSize, u_cornerRadii)"));
        assertTrue(shader.contains("float sdRoundedRectRealistic("));
    }

    @Test
    public void compositePositionsLocalTextureInLogicalOutputCoordinates() {
        String vertex = PrismalBloomShaderSources.COMPOSITE_VERTEX;
        String fragment = PrismalBloomShaderSources.COMPOSITE_FRAGMENT;

        assertTrue(vertex.contains("uniform vec2 u_resolution;"));
        assertTrue(vertex.contains("uniform vec2 u_centerPx;"));
        assertTrue(vertex.contains("uniform vec2 u_targetSize;"));
        assertTrue(vertex.contains("vec2 localPx = aPosition * 0.5 * u_targetSize;"));
        assertTrue(vertex.contains("vec2 screenPx = u_centerPx + localPx;"));
        assertTrue(vertex.contains("vec2 ndc = screenPx / u_resolution * 2.0 - 1.0;"));
        assertTrue(fragment.contains("uniform sampler2D uTexture;"));
        assertTrue(fragment.contains("gl_FragColor = texture2D(uTexture, vUv);"));
    }
}
