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
    public void visibleRecentsConsumerKeepsSharedSourceLive() {
        assertTrue(WorkstationProducerPolicy.keepSharedSourceLiveForRecents(true, true));
        assertFalse("No visible child means no Recents capture lease",
                WorkstationProducerPolicy.keepSharedSourceLiveForRecents(false, true));
        assertFalse(WorkstationProducerPolicy.keepSharedSourceLiveForRecents(true, false));
        // A modal HOME folder does not remove a visible Recents OES consumer's lease.
    }

    @Test
    public void recentsConsumerReleaseRespectsFolderAndWallpaperAuthorities() {
        assertTrue(WorkstationProducerPolicy.shouldPauseAfterRecentsConsumerGone(
                true, false));
        assertTrue(WorkstationProducerPolicy.shouldPauseAfterRecentsConsumerGone(
                true, true));
        assertTrue(WorkstationProducerPolicy.shouldPauseAfterRecentsConsumerGone(
                false, false));
        assertFalse(WorkstationProducerPolicy.shouldPauseAfterRecentsConsumerGone(
                false, true));
    }

    @Test
    public void normalWorkspaceRefreshUsesPulseAndWorkstationRemainsLive() {
        assertFalse(WorkstationProducerPolicy.shouldUseSingleFramePulse(true));
        assertTrue(WorkstationProducerPolicy.shouldUseSingleFramePulse(false));
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
