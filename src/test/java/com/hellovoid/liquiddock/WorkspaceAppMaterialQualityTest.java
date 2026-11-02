package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import org.junit.Test;

public class WorkspaceAppMaterialQualityTest {
    @Test
    public void idleAndCancelledTransitionsUseCompleteMaterial() {
        assertEquals(1f, WorkspaceAppMaterialQuality.detail(false, 0f), 0f);
        assertEquals(1f, WorkspaceAppMaterialQuality.detail(false, .5f), 0f);
    }

    @Test
    public void expensiveMaterialIsDeferredUntilNativeSpringTail() {
        assertEquals(0f, WorkspaceAppMaterialQuality.detail(true, 0f), 0f);
        assertEquals(0f, WorkspaceAppMaterialQuality.detail(true, .92f), 0f);
        float middle = WorkspaceAppMaterialQuality.detail(true, .96f);
        assertTrue(middle > 0f && middle < 1f);
        assertEquals(1f, WorkspaceAppMaterialQuality.detail(true, .999f), 0f);
        assertEquals(1f, WorkspaceAppMaterialQuality.detail(true, 1.2f), 0f);
    }

    @Test
    public void recoveryNeverFallsBackDuringSpringReboundOrGeometryTail() {
        WorkspaceAppMaterialQuality quality = new WorkspaceAppMaterialQuality();
        quality.begin();
        quality.update(true, true, .98f);
        float reached = quality.currentDetail();
        quality.update(true, true, .95f);
        assertEquals(reached, quality.currentDetail(), 0f);
        quality.update(true, true, 1f);
        quality.update(false, true, 0f);
        assertTrue(quality.usesTransitionProgram());
        assertEquals(1f, quality.currentDetail(), 0f);
        quality.update(false, false, 0f);
        assertFalse(quality.usesTransitionProgram());
        assertEquals(1f, quality.currentDetail(), 0f);
        quality.begin();
        assertEquals(0f, quality.currentDetail(), 0f);
    }

    @Test
    public void retargetAndUnknownProgressCannotLeaveFullWorkRunningEarly() {
        assertEquals(0f, WorkspaceAppMaterialQuality.detail(true, Float.NaN), 0f);
        assertEquals(0f, WorkspaceAppMaterialQuality.detail(true, -.2f), 0f);
        assertTrue(
                WorkspaceAppMaterialQuality.detail(true, .99f)
                        > WorkspaceAppMaterialQuality.detail(true, .2f));
    }

    @Test
    public void exponentialNativeTailDoesNotFrontLoadTheVisibleRecovery() {
        assertTrue(WorkspaceAppMaterialQuality.detail(true, .96f) < .2f);
        float middle = WorkspaceAppMaterialQuality.detail(true, .99f);
        assertTrue(middle > .25f && middle < .75f);
    }
}
