package com.hellovoid.liquiddock;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WorkstationGridMarginPolicyTest {
    @Test
    public void configuredSpacingReplacesAsymmetricNativeMargins() {
        // Horizontal remains symmetric, but top and bottom are independent absolute edges.
        assertArrayEquals(new int[]{12, 12, 8, 20},
                WorkstationGridMarginPolicy.apply(10, 20, 5, 0, 12, 8, 20));
        assertArrayEquals(new int[]{12, 12, 8, 20},
                WorkstationGridMarginPolicy.apply(90, 3, 44, 71, 12, 8, 20));
    }

    @Test
    public void absoluteSpacingCannotBecomeNegative() {
        assertArrayEquals(new int[]{0, 0, 0, 0},
                WorkstationGridMarginPolicy.apply(10, 20, 5, 7, -15, -10, -30));
    }

    @Test
    public void allAppsDetectionDoesNotDependOnOnePrivateLauncherMethod() {
        assertTrue(WorkstationLayoutClassifier.matches(true, "", ""));
        assertTrue(WorkstationLayoutClassifier.matches(false,
                "GRID_TYPE_IN_ALL_APPS_WORKSPACE", ""));
        assertTrue(WorkstationLayoutClassifier.matches(false, "",
                "com.miui.home.launcher.laptop.AllAppsContainer>android.widget.FrameLayout"));
        assertFalse(WorkstationLayoutClassifier.matches(false,
                "GRID_TYPE_WORKSPACE", "com.miui.home.launcher.Workspace"));
    }
}
