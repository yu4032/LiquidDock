package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeGridInstallConfigGuardTest {
    @Test
    public void legacyConstructorKeepsPlacementGuardEnabledByDefault() {
        HomeGridInstallConfig config = new HomeGridInstallConfig(
                true, 8, 3,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0,
                0, 0,
                0, 1f,
                false);
        assertTrue(config.widgetPlacementGuard);
    }

    @Test
    public void explicitDisabledGuardSurvivesDerivedZeroOffsetConfig() {
        HomeGridInstallConfig config = new HomeGridInstallConfig(
                true, 8, 3,
                10, 10, 0, 0,
                10, 10, 0, 0,
                0, 0,
                0, 0,
                0, 1f,
                false,
                false);
        assertFalse(config.widgetPlacementGuard);
        assertFalse(config.withZeroHorizontalOffsets().widgetPlacementGuard);
    }
}
