package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure generation/ownership contract for Launcher Workspace terminal recovery. */
public class LauncherGlassTerminalRecoveryPolicyTest {
    @Test
    public void activationFailureWithoutGenerationBelongsToCurrentWorkspaceSession() {
        assertTrue(LauncherGlassTerminalRecoveryPolicy.shouldSelfRecover(-1L, 7L, false));
    }

    @Test
    public void currentSceneFailureMaySelfRecover() {
        assertTrue(LauncherGlassTerminalRecoveryPolicy.shouldSelfRecover(7L, 7L, false));
    }

    @Test
    public void stalePositiveGenerationCannotRecoverCurrentScene() {
        assertFalse(LauncherGlassTerminalRecoveryPolicy.shouldSelfRecover(6L, 7L, false));
    }

    @Test
    public void externallyOwnedSecondarySessionKeepsItsOwnFailureLifecycle() {
        assertFalse(LauncherGlassTerminalRecoveryPolicy.shouldSelfRecover(-1L, 7L, true));
        assertFalse(LauncherGlassTerminalRecoveryPolicy.shouldSelfRecover(7L, 7L, true));
    }
}
