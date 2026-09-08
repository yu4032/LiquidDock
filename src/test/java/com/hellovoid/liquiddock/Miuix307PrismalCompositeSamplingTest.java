package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Miuix307PrismalCompositeSamplingTest {
    @Test
    public void finalCropSamplesInsideSourceTexelCenters() {
        String fragment = Miuix307PrismalCompositeShaders.FRAGMENT;

        assertTrue(fragment.contains("uniform vec2 uTextureSize;"));
        assertTrue(fragment.contains("vec2 halfTexel = 0.5 / max(uTextureSize, vec2(1.0));"));
        assertTrue(fragment.contains("vec2 cropMin = uCropRect.xy + halfTexel;"));
        assertTrue(fragment.contains(
                "vec2 cropMax = uCropRect.xy + uCropRect.zw - halfTexel;"));
        assertTrue(fragment.contains("vec2 uv = mix(cropMin, cropMax, vUv);"));
        assertFalse(fragment.contains("uCropRect.xy + vUv * uCropRect.zw"));
    }
}
