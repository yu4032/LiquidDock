package com.hellovoid.liquiddock;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class LauncherInstallSequenceTest {
    @Test
    public void zeroCopyPathPreservesHistoricalPrefixAndEarlyReturnBoundary() {
        LauncherInstallSequence sequence = new LauncherInstallSequence();
        sequence.advance(LauncherInstallSequence.Stage.WORKSTATION_RUNTIME);
        sequence.advance(LauncherInstallSequence.Stage.CONFIG_LOADED);
        sequence.advance(LauncherInstallSequence.Stage.DOCK_FOUNDATION);
        sequence.advance(LauncherInstallSequence.Stage.GRID);
        sequence.advance(LauncherInstallSequence.Stage.DOCK_SHADOW);
        sequence.advance(LauncherInstallSequence.Stage.GLASS_DECISION);
        sequence.finishAtGlassOwner();
    }

    @Test
    public void fallbackPathRunsOnlyAfterGlassDecision() {
        LauncherInstallSequence sequence = new LauncherInstallSequence();
        sequence.advance(LauncherInstallSequence.Stage.WORKSTATION_RUNTIME);
        sequence.advance(LauncherInstallSequence.Stage.CONFIG_LOADED);
        sequence.advance(LauncherInstallSequence.Stage.DOCK_FOUNDATION);
        sequence.advance(LauncherInstallSequence.Stage.GRID);
        sequence.advance(LauncherInstallSequence.Stage.DOCK_SHADOW);
        sequence.advance(LauncherInstallSequence.Stage.GLASS_DECISION);
        sequence.advance(LauncherInstallSequence.Stage.FALLBACK_DOCK);
        sequence.finishFallback();
    }

    @Test
    public void reorderingFeaturesIsRejectedByTypedProductionGuard() {
        LauncherInstallSequence sequence = new LauncherInstallSequence();
        sequence.advance(LauncherInstallSequence.Stage.WORKSTATION_RUNTIME);
        sequence.advance(LauncherInstallSequence.Stage.CONFIG_LOADED);

        assertThrows(
                IllegalStateException.class,
                () -> sequence.advance(LauncherInstallSequence.Stage.GRID));
    }
}
