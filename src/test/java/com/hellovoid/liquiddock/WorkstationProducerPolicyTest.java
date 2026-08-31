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
    public void workstationRefreshUsesContinuousCaptureInsteadOfDelayedPausePulse() {
        assertFalse(WorkstationProducerPolicy.shouldUseSingleFramePulse(true));
        assertTrue(WorkstationProducerPolicy.shouldUseSingleFramePulse(false));
    }

    @Test
    public void normalDockProducerIsDemandDrivenButWorkstationRemainsContinuous() {
        assertFalse(WorkstationProducerPolicy.shouldKeepDockProducerContinuous(false));
        assertTrue(WorkstationProducerPolicy.shouldKeepDockProducerContinuous(true));
    }

    @Test
    public void workstationGeometryChangeRebindsSharedProducer() {
        assertTrue(WorkstationProducerPolicy.shouldRebindForGeometryChange(true, true));
        assertFalse(WorkstationProducerPolicy.shouldRebindForGeometryChange(true, false));
        assertFalse(WorkstationProducerPolicy.shouldRebindForGeometryChange(false, true));
    }
}
