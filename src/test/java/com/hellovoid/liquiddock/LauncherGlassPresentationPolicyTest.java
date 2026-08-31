package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LauncherGlassPresentationPolicyTest {
    @Test public void continuousNativeAlphaTracksDirectly() {
        LauncherGlassPresentationPolicy.Decision decision =
                LauncherGlassPresentationPolicy.decide(false, true, true, 0.42f);
        assertEquals(LauncherGlassPresentationPolicy.Mode.DIRECT, decision.mode);
        assertEquals(0.42f, decision.targetAlpha, 0f);
        assertFalse(decision.retainLastGeometry);
    }

    @Test public void hardVisibilityHideUsesReversibleFadeAndRetainsGeometry() {
        LauncherGlassPresentationPolicy.Decision decision =
                LauncherGlassPresentationPolicy.decide(false, true, false, 0f);
        assertEquals(LauncherGlassPresentationPolicy.Mode.ANIMATE, decision.mode);
        assertEquals(0f, decision.targetAlpha, 0f);
        assertTrue(decision.retainLastGeometry);
    }

    @Test public void hardVisibilityRevealAnimatesFromCurrentPresentation() {
        LauncherGlassPresentationPolicy.Decision decision =
                LauncherGlassPresentationPolicy.decide(false, false, true, 1f);
        assertEquals(LauncherGlassPresentationPolicy.Mode.ANIMATE, decision.mode);
        assertEquals(1f, decision.targetAlpha, 0f);
        assertFalse(decision.retainLastGeometry);
    }

    @Test public void partialRevealTracksVendorAlphaInsteadOfAddingSecondAnimation() {
        LauncherGlassPresentationPolicy.Decision decision =
                LauncherGlassPresentationPolicy.decide(false, false, true, 0.35f);
        assertEquals(LauncherGlassPresentationPolicy.Mode.DIRECT, decision.mode);
        assertEquals(0.35f, decision.targetAlpha, 0f);
    }

    @Test public void semanticSceneOwnershipBypassesNativeAlpha() {
        LauncherGlassPresentationPolicy.Decision decision =
                LauncherGlassPresentationPolicy.decide(true, false, false, 0f);
        assertEquals(LauncherGlassPresentationPolicy.Mode.BYPASS, decision.mode);
        assertEquals(1f, decision.targetAlpha, 0f);
        assertTrue(decision.retainLastGeometry);
    }

    @Test public void suppressionAndNativePresentationMultiply() {
        assertEquals(0.30f,
                LauncherGlassPresentationPolicy.composeAlpha(0.60f, 0.50f), 0.0001f);
        assertEquals(0f,
                LauncherGlassPresentationPolicy.composeAlpha(Float.NaN, 0.50f), 0f);
        assertEquals(1f,
                LauncherGlassPresentationPolicy.composeAlpha(2f, 2f), 0f);
    }
}
