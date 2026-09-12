package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PassBlurBindPolicyTest {
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
    public void securityCenterPausesAfterFreshSourceWhileContinuousDomainsStayLive() {
        assertTrue(PassBlurBindPolicy.shouldPauseAfterFreshFrame(PassBlurDomain.SECURITY_CENTER));
        assertFalse(PassBlurBindPolicy.shouldPauseAfterFreshFrame(PassBlurDomain.LAUNCHER_WORKSPACE));
        assertFalse(PassBlurBindPolicy.shouldPauseAfterFreshFrame(PassBlurDomain.DOCK));
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
