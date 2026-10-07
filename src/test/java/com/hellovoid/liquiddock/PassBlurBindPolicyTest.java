package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PassBlurBindPolicyTest {
    @Test
    public void unavailableUnlockEndpointDoesNotExhaustWorkspaceBindBudget() {
        assertEquals(0, PassBlurBindPolicy.retryBudgetAttempt(
                PassBlurDomain.LAUNCHER_WORKSPACE, 24, true, true));
        assertEquals(24, PassBlurBindPolicy.retryBudgetAttempt(
                PassBlurDomain.LAUNCHER_WORKSPACE, 24, false, true));
        assertEquals(24, PassBlurBindPolicy.retryBudgetAttempt(
                PassBlurDomain.LAUNCHER_WORKSPACE, 24, true, false));
    }

    @Test
    public void unlockReadinessDoesNotExtendOtherDomainsBindBudgets() {
        for (PassBlurDomain domain : PassBlurDomain.values()) {
            if (domain == PassBlurDomain.LAUNCHER_WORKSPACE) continue;
            assertEquals(domain.name(), 24,
                    PassBlurBindPolicy.retryBudgetAttempt(domain, 24, true, true));
        }
    }

    @Test
    public void nativeScaleKeepsAuthoritativeRootGeometryAtFullScale() {
        assertEquals(1.0f,
                PassBlurBindPolicy.nativeScale(PassBlurDomain.SECURITY_CENTER, 0.25f),
                0.0001f);
        assertEquals(1.0f,
                PassBlurBindPolicy.nativeScale(PassBlurDomain.LAUNCHER_WORKSPACE, 0.75f),
                0.0001f);
    }

    @Test
    public void onlyLauncherWorkspaceRequiresUnlockGate() {
        assertTrue(PassBlurBindPolicy.requiresUnlockGate(PassBlurDomain.LAUNCHER_WORKSPACE));
        assertFalse(PassBlurBindPolicy.requiresUnlockGate(PassBlurDomain.DOCK));
        assertFalse(PassBlurBindPolicy.requiresUnlockGate(PassBlurDomain.SECURITY_CENTER));
    }

    @Test
    public void exclusionsUseRuntimeRootAndRemoveDuplicates() {
        assertArrayEquals(
                new String[]{
                        "DockAssistantView#42",
                        "NavigationBar",
                        "StatusBar",
                        "GestureStub",
                        "DockAssistantView",
                        "CustomOverlay"
                },
                PassBlurBindPolicy.exclusions(
                        "DockAssistantView#42",
                        new String[]{
                                "DockAssistantView",
                                "StatusBar",
                                "DockAssistantView#42",
                                "CustomOverlay",
                                "DockAssistantView"
                        }));
    }
}
