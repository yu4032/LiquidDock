package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class SecurityCenterGlassRuntimeTransitionPolicyTest {
    @Test
    public void effectiveStateRequiresCoreGlassAndSecurityCenterSwitches() {
        assertTrue(new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(true, true, true)
                .effective());
        assertFalse(new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(false, true, true)
                .effective());
        assertFalse(new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(true, false, true)
                .effective());
        assertFalse(new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(true, true, false)
                .effective());
    }

    @Test
    public void anyEffectiveDisableReleasesAllOwnership() {
        SecurityCenterGlassRuntimeTransitionPolicy.Snapshot before =
                new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(true, true, true);

        assertTrue(SecurityCenterGlassRuntimeTransitionPolicy.plan(before,
                new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(false, true, true))
                .releaseAll);
        assertTrue(SecurityCenterGlassRuntimeTransitionPolicy.plan(before,
                new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(true, false, true))
                .releaseAll);
        assertTrue(SecurityCenterGlassRuntimeTransitionPolicy.plan(before,
                new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(true, true, false))
                .releaseAll);
    }

    @Test
    public void inactiveTransitionsDoNotReleaseAllOwnership() {
        SecurityCenterGlassRuntimeTransitionPolicy.Snapshot inactive =
                new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(false, true, true);

        assertFalse(SecurityCenterGlassRuntimeTransitionPolicy.plan(inactive,
                new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(false, false, false))
                .releaseAll);
        assertFalse(SecurityCenterGlassRuntimeTransitionPolicy.plan(inactive,
                new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(true, true, true))
                .releaseAll);
    }

    @Test
    public void gameToolboxRestoresVendorWhileVideoAndGlobalDockKeepCustomGlass() {
        SecurityCenterGlassRuntimeTransitionPolicy.AssistantTransition game =
                SecurityCenterGlassRuntimeTransitionPolicy.planAssistant(1);
        assertFalse(game.bindCustomGlass);
        assertTrue(game.releaseExistingCustomGlass);

        SecurityCenterGlassRuntimeTransitionPolicy.AssistantTransition video =
                SecurityCenterGlassRuntimeTransitionPolicy.planAssistant(3);
        assertTrue(video.bindCustomGlass);
        assertFalse(video.releaseExistingCustomGlass);

        SecurityCenterGlassRuntimeTransitionPolicy.AssistantTransition globalDock =
                SecurityCenterGlassRuntimeTransitionPolicy.planAssistant(4);
        assertTrue(globalDock.bindCustomGlass);
        assertFalse(globalDock.releaseExistingCustomGlass);

        SecurityCenterGlassRuntimeTransitionPolicy.AssistantTransition unknown =
                SecurityCenterGlassRuntimeTransitionPolicy.planAssistant(99);
        assertFalse(unknown.bindCustomGlass);
        assertFalse(unknown.releaseExistingCustomGlass);
    }

    @Test
    public void vendorOnlyGameDoesNotWaitForCustomPreparation() {
        SecurityCenterGlassRuntimeTransitionPolicy.AssistantTransition game =
                SecurityCenterGlassRuntimeTransitionPolicy.planAssistant(1);
        assertFalse(game.requiresDeferredPrepare);

        SecurityCenterGlassRuntimeTransitionPolicy.AssistantTransition video =
                SecurityCenterGlassRuntimeTransitionPolicy.planAssistant(3);
        assertTrue(video.requiresDeferredPrepare);

        SecurityCenterGlassRuntimeTransitionPolicy.AssistantTransition globalDock =
                SecurityCenterGlassRuntimeTransitionPolicy.planAssistant(4);
        assertTrue(globalDock.requiresDeferredPrepare);

        SecurityCenterGlassRuntimeTransitionPolicy.AssistantTransition unknown =
                SecurityCenterGlassRuntimeTransitionPolicy.planAssistant(99);
        assertFalse(unknown.requiresDeferredPrepare);
    }

    @Test
    public void videoAndGlobalRearmOnEverySidebarShowWhileGameNeverDoes() {
        SecurityCenterGlassRuntimeTransitionPolicy.AssistantTransition game =
                SecurityCenterGlassRuntimeTransitionPolicy.planAssistant(1);
        assertFalse(game.rearmOnSidebarShow);

        SecurityCenterGlassRuntimeTransitionPolicy.AssistantTransition video =
                SecurityCenterGlassRuntimeTransitionPolicy.planAssistant(3);
        assertTrue(video.rearmOnSidebarShow);

        SecurityCenterGlassRuntimeTransitionPolicy.AssistantTransition globalDock =
                SecurityCenterGlassRuntimeTransitionPolicy.planAssistant(4);
        assertTrue(globalDock.rearmOnSidebarShow);

        SecurityCenterGlassRuntimeTransitionPolicy.AssistantTransition unknown =
                SecurityCenterGlassRuntimeTransitionPolicy.planAssistant(99);
        assertFalse(unknown.rearmOnSidebarShow);
    }

    @Test
    public void videoAndGlobalUseCustomShaderWhileGameStaysVendorOnly() {
        SecurityCenterGlassRuntimeTransitionPolicy.AssistantTransition game =
                SecurityCenterGlassRuntimeTransitionPolicy.planAssistant(1);
        assertEquals(SecurityCenterGlassRuntimeTransitionPolicy.AssistantBackend.VENDOR_ONLY,
                game.backend);

        SecurityCenterGlassRuntimeTransitionPolicy.AssistantTransition video =
                SecurityCenterGlassRuntimeTransitionPolicy.planAssistant(3);
        assertEquals(SecurityCenterGlassRuntimeTransitionPolicy.AssistantBackend.CUSTOM_SHADER,
                video.backend);

        SecurityCenterGlassRuntimeTransitionPolicy.AssistantTransition globalDock =
                SecurityCenterGlassRuntimeTransitionPolicy.planAssistant(4);
        assertEquals(SecurityCenterGlassRuntimeTransitionPolicy.AssistantBackend.CUSTOM_SHADER,
                globalDock.backend);

        SecurityCenterGlassRuntimeTransitionPolicy.AssistantTransition unknown =
                SecurityCenterGlassRuntimeTransitionPolicy.planAssistant(99);
        assertEquals(SecurityCenterGlassRuntimeTransitionPolicy.AssistantBackend.IGNORE,
                unknown.backend);
    }

    @Test
    public void gameToolboxBindReleasesExistingRuntimeOwner() {
        AtomicInteger releases = new AtomicInteger();
        SecurityCenterGlassRuntimeState.setOwner(releases::incrementAndGet);
        try {
            SecurityCenterGlassRuntimeState.bindAssistant(null, null, null, 1);
            assertEquals(1, releases.get());
        } finally {
            SecurityCenterGlassRuntimeState.setOwner(null);
        }
    }
}
