package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class LauncherIconGlassMotionPolicyTest {
    @Test public void closeToHomeEndsAtTheGlassRadiusWithNativeProgress() {
        assertEquals(14f, LauncherIconGlassMotionPolicy.radius(14f, 32f, 0f, true), 0f);
        assertEquals(23f, LauncherIconGlassMotionPolicy.radius(14f, 32f, .5f, true), 0f);
        assertEquals(32f, LauncherIconGlassMotionPolicy.radius(14f, 32f, 1f, true), 0f);
    }

    @Test public void appLaunchStartsAtGlassAndEndsAtNativeRadius() {
        assertEquals(32f, LauncherIconGlassMotionPolicy.radius(14f, 32f, 0f, false), 0f);
        assertEquals(23f, LauncherIconGlassMotionPolicy.radius(14f, 32f, .5f, false), 0f);
        assertEquals(14f, LauncherIconGlassMotionPolicy.radius(14f, 32f, 1f, false), 0f);
    }

    @Test public void followsChangingNativeAnimatedRadiusInsteadOfFixedOriginalMask() {
        assertEquals(48f, LauncherIconGlassMotionPolicy.radius(64f, 32f, .5f, true), 0f);
        assertEquals(32f, LauncherIconGlassMotionPolicy.radius(3f, 32f, 1f, true), 0f);
        assertEquals(64f, LauncherIconGlassMotionPolicy.radius(64f, 32f, 0f, true), 0f);
    }

    @Test public void clampsProgressButRejectsInvalidGeometry() {
        assertEquals(0f, LauncherIconGlassMotionPolicy.iconWeight(-1f, true), 0f);
        assertEquals(1f, LauncherIconGlassMotionPolicy.iconWeight(3f, true), 0f);
        assertTrue(Float.isNaN(LauncherIconGlassMotionPolicy.radius(Float.NaN, 32f, .5f, true)));
        assertTrue(Float.isNaN(LauncherIconGlassMotionPolicy.radius(12f, -2f, .5f, true)));
        assertTrue(Float.isNaN(LauncherIconGlassMotionPolicy.localRadius(20f, 0f)));
        assertEquals(10f, LauncherIconGlassMotionPolicy.localRadius(20f, 2f), 0f);
    }

    @Test public void layerScaleMatchesVendorPortraitAndLandscapeBranches() {
        assertEquals(3f, LauncherIconGlassMotionPolicy.layerScale(120f, 300f, 50f, 100f), 0f);
        assertEquals(2f, LauncherIconGlassMotionPolicy.layerScale(200f, 80f, 100f, 60f), 0f);
        assertTrue(Float.isNaN(LauncherIconGlassMotionPolicy.layerScale(0f, 40f, 100f, 100f)));
    }
}
