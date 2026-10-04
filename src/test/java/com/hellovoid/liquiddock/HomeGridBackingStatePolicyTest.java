package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeGridBackingStatePolicyTest {
    @Test
    public void portraitTransitionDefersWhileLandscapeBackingIsStillInstalled() {
        assertFalse(HomeGridBackingStatePolicy.canApplyGeometry(4, 7, 7, 4));
        assertFalse(HomeGridBackingStatePolicy.canApplyGeometry(6, 10, 10, 6));
    }

    @Test
    public void splitTransitionDefersUntilItsBackingMatrixMatches() {
        assertFalse(HomeGridBackingStatePolicy.canApplyGeometry(4, 7, 6, 4));
        assertTrue(HomeGridBackingStatePolicy.canApplyGeometry(4, 7, 4, 7));
    }

    @Test
    public void absentBackingDoesNotBlockFirstLayoutGeometry() {
        assertTrue(HomeGridBackingStatePolicy.canApplyGeometry(7, 4, 0, 0));
        assertFalse(HomeGridBackingStatePolicy.canApplyGeometry(0, 4, 0, 0));
    }
}
