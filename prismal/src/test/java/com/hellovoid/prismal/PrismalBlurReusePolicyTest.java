package com.hellovoid.prismal;

import static org.junit.Assert.*;
import org.junit.Test;

public class PrismalBlurReusePolicyTest {
    @Test public void sameEffectiveKernelReusesThePreparedBlur() {
        float first = PrismalBlurReusePolicy.sigma(0f, 1200, 540, 2400, 1080, .5f);
        float equivalent = PrismalBlurReusePolicy.sigma(1f, 1200, 540, 2400, 1080, .5f);
        assertEquals(.5f, first, 0f);
        assertEquals(first, equivalent, 0f);
        assertFalse(PrismalBlurReusePolicy.needsReblur(first, equivalent));
    }
    @Test public void distinctKernelsAndMissingCacheAreRecomputed() {
        float sidebar = PrismalBlurReusePolicy.sigma(12f, 1200, 540, 2400, 1080, .5f);
        float apps = PrismalBlurReusePolicy.sigma(30f, 1200, 540, 2400, 1080, .5f);
        assertTrue(PrismalBlurReusePolicy.needsReblur(Float.NaN, sidebar));
        assertTrue(PrismalBlurReusePolicy.needsReblur(sidebar, apps));
        assertFalse(PrismalBlurReusePolicy.needsReblur(apps, apps));
        assertTrue("returning to sidebar must restore its kernel",
                PrismalBlurReusePolicy.needsReblur(apps, sidebar));
    }
    @Test public void physicalScaleUsesTheActualShortestAxis() {
        assertEquals(.8f, PrismalBlurReusePolicy.sigma(8f, 1000, 200, 2000, 1000, .5f), 0.000001f);
    }
}
