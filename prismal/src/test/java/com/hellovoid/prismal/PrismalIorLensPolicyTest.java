package com.hellovoid.prismal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PrismalIorLensPolicyTest {
    @Test public void normalizesExistingDefaultAndBecomesZeroInAir() {
        assertEquals(0f, PrismalIorLensPolicy.relativeBend(1f), 1e-6f);
        assertEquals(1f, PrismalIorLensPolicy.relativeBend(1.55f), 1e-6f);
        assertEquals(1f, PrismalIorLensPolicy.relativeBend(Float.NaN), 1e-6f);
        assertEquals(1f, PrismalIorLensPolicy.relativeBend(Float.POSITIVE_INFINITY), 1e-6f);
    }

    @Test public void strongerRefractionRaisesTransmittedEdgeBend() {
        float n120 = PrismalIorLensPolicy.relativeBend(1.2f);
        float n155 = PrismalIorLensPolicy.relativeBend(1.55f);
        float n200 = PrismalIorLensPolicy.relativeBend(2f);
        assertTrue(n120 > 0f && n120 < n155);
        assertTrue(n200 > n155 && n200 < 1.5f);
    }

    @Test public void samplingGuardCoversIncreasedRefractiveLensReach() {
        PrismalParams.Builder low = PrismalParams.builder();
        low.ior = 1.0f;
        low.heightTransitionWidthPx = 32f;
        low.lensRefractionScale = 2f;
        PrismalParams.Builder high = PrismalParams.builder();
        high.ior = 2.0f;
        high.heightTransitionWidthPx = 32f;
        high.lensRefractionScale = 2f;
        int lowGuard = PrismalSampling.requiredGuardPx(low.build(), 360f, 180f, true);
        int highGuard = PrismalSampling.requiredGuardPx(high.build(), 360f, 180f, true);
        assertTrue(highGuard > lowGuard);
    }
}
