package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LauncherGlassVisibilityTransitionTest {
    @Test public void fullFadeUsesConfiguredDuration() {
        LauncherGlassVisibilityTransition.Plan plan =
                LauncherGlassVisibilityTransition.plan(1f, false);
        assertEquals(0f, plan.targetAlpha, 0f);
        assertEquals(450L, plan.durationMs);
    }

    @Test public void reversalContinuesFromCurrentAlphaAtConstantSpeed() {
        LauncherGlassVisibilityTransition.Plan plan =
                LauncherGlassVisibilityTransition.plan(0.4f, true);
        assertEquals(1f, plan.targetAlpha, 0f);
        assertEquals(270L, plan.durationMs);
    }

    @Test public void valuesAreClampedAndNoOpHasNoDuration() {
        LauncherGlassVisibilityTransition.Plan plan =
                LauncherGlassVisibilityTransition.plan(2f, true);
        assertEquals(1f, plan.startAlpha, 0f);
        assertEquals(0L, plan.durationMs);
    }
}
