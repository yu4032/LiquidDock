package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PassBlurQualityPolicyTest {
    @Test
    public void captureScaleIsClampedToSafeExperimentalRange() {
        assertEquals(0.50f, PassBlurQualityPolicy.captureScale(10), 0.0001f);
        assertEquals(0.75f, PassBlurQualityPolicy.captureScale(75), 0.0001f);
        assertEquals(1.00f, PassBlurQualityPolicy.captureScale(120), 0.0001f);
    }

    @Test
    public void workspaceNativePassBlurStaysFullScaleAndDockStaysValidatedFullScale() {
        assertEquals(1.00f, PassBlurQualityPolicy.bridgeScale(true, 75), 0.0001f);
        assertEquals(1.00f, PassBlurQualityPolicy.bridgeScale(false, 50), 0.0001f);
    }

    @Test
    public void sourceFreshnessBypassesConsumerRateLimit() {
        assertTrue(PassBlurQualityPolicy.requiresFreshConsumerFrame(-1L, 2L));
        assertTrue(PassBlurQualityPolicy.requiresFreshConsumerFrame(1L, 2L));
        assertFalse(PassBlurQualityPolicy.requiresFreshConsumerFrame(2L, 2L));
    }

    @Test
    public void renderFpsHasNoFixedSixtyHertzCeiling() {
        assertEquals(60, PassBlurQualityPolicy.renderFps(60));
        assertEquals(120, PassBlurQualityPolicy.renderFps(120));
        assertEquals(165, PassBlurQualityPolicy.renderFps(165));
        assertEquals(240, PassBlurQualityPolicy.renderFps(240));
    }

    @Test
    public void globalCapOnlyLowersProfileSpecificLimits() {
        assertEquals(120, PassBlurQualityPolicy.applyGlobalRenderFpsCap(165, 120));
        assertEquals(90, PassBlurQualityPolicy.applyGlobalRenderFpsCap(90, 120));
        assertEquals(120, PassBlurQualityPolicy.applyGlobalRenderFpsCap(0, 120));
        assertEquals(90, PassBlurQualityPolicy.applyGlobalRenderFpsCap(90, 0));
    }

    @Test
    public void renderFpsZeroMeansSourceDrivenAuto() {
        PassBlurFrameRateLimiter limiter = new PassBlurFrameRateLimiter(0);
        assertTrue(limiter.shouldSchedule(0L, false));
        assertTrue(limiter.shouldSchedule(1L, false));
    }

    @Test
    public void limiterDropsIntermediateFiftyHertzFramesButKeepsAboutThirtyFps() {
        PassBlurFrameRateLimiter limiter = new PassBlurFrameRateLimiter(30);
        int accepted = 0;
        for (int i = 0; i < 50; i++) {
            if (limiter.shouldSchedule(i * 20_000_000L, false)) accepted++;
        }
        assertTrue("accepted=" + accepted, accepted >= 29 && accepted <= 31);
    }

    @Test
    public void freshnessFrameBypassesLimiterWithoutDestroyingCadenceState() {
        PassBlurFrameRateLimiter limiter = new PassBlurFrameRateLimiter(30);
        assertTrue(limiter.shouldSchedule(0L, false));
        assertFalse(limiter.shouldSchedule(10_000_000L, false));
        assertTrue(limiter.shouldSchedule(10_000_000L, true));
        assertFalse(limiter.shouldSchedule(20_000_000L, false));
    }

    @Test
    public void idleTimeDoesNotBankRenderCreditsForTheNextTransition() {
        PassBlurFrameRateLimiter limiter = new PassBlurFrameRateLimiter(60);
        assertTrue(limiter.shouldSchedule(0L, false));
        long resumedAt = 60_000_000_000L;
        assertTrue(limiter.shouldSchedule(resumedAt, false));
        assertFalse("a resume frame must consume the idle backlog",
                limiter.shouldSchedule(resumedAt + 6_060_606L, false));
        int accepted = 1;
        for (int i = 2; i < 165; i++) {
            if (limiter.shouldSchedule(resumedAt + i * 6_060_606L, false)) accepted++;
        }
        assertTrue("accepted=" + accepted, accepted >= 59 && accepted <= 61);
    }

    @Test
    public void lateSourceFrameDropsMissedSlotsInsteadOfCatchingUp() {
        PassBlurFrameRateLimiter limiter = new PassBlurFrameRateLimiter(30);
        assertTrue(limiter.shouldSchedule(0L, false));
        assertTrue(limiter.shouldSchedule(500_000_000L, false));
        assertFalse(limiter.shouldSchedule(506_000_000L, false));
        assertTrue(limiter.shouldSchedule(540_000_000L, false));
    }
}
