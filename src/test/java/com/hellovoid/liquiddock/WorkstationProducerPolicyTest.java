package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WorkstationProducerPolicyTest {
    @Test
    public void coveredWorkspaceCannotPauseSharedProducerInWorkstationMode() {
        assertFalse(WorkstationProducerPolicy.shouldPauseSharedProducer(true, true));
    }

    @Test
    public void normalModeRetainsStaticWorkspacePowerPolicy() {
        assertTrue(WorkstationProducerPolicy.shouldPauseSharedProducer(true, false));
        assertFalse(WorkstationProducerPolicy.shouldPauseSharedProducer(false, false));
    }

    @Test
    public void recentsKeepsSharedSourceLiveWhileCovered() {
        assertTrue(WorkstationProducerPolicy.keepSharedSourceLiveForRecents(true, true));
        assertFalse(WorkstationProducerPolicy.keepSharedSourceLiveForRecents(false, true));
        assertFalse(WorkstationProducerPolicy.keepSharedSourceLiveForRecents(true, false));
        // No folder-state argument: a modal HOME folder must not remove
        // Recents' right to use an already shared OES producer.
    }

    @Test
    public void workspaceRefreshUsesContinuousCaptureInAllModes() {
        assertFalse(WorkstationProducerPolicy.shouldUseSingleFramePulse(true));
        assertFalse(WorkstationProducerPolicy.shouldUseSingleFramePulse(false));
    }

    @Test
    public void consumingWorkspaceFrameKeepsLiveProducerRunningInAllModes() {
        assertFalse(WorkstationProducerPolicy.shouldPauseAfterFrameConsumed(true));
        assertFalse(WorkstationProducerPolicy.shouldPauseAfterFrameConsumed(false));
    }

    @Test
    public void workstationGeometryChangeRebindsSharedProducer() {
        assertTrue(WorkstationProducerPolicy.shouldRebindForGeometryChange(true, true));
        assertFalse(WorkstationProducerPolicy.shouldRebindForGeometryChange(true, false));
        assertFalse(WorkstationProducerPolicy.shouldRebindForGeometryChange(false, true));
    }
}
