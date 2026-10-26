package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WorkspacePassBlurRolloverPolicyTest {
    @Test
    public void destroyedOldWorkspaceCanRecoverOnDistinctLiveViewRoot() {
        assertTrue(WorkspacePassBlurRolloverPolicy.isolate(true, false, true, false));
    }

    @Test
    public void keepNormalCommittedDetachForLiveOrSameGeneration() {
        assertFalse(WorkspacePassBlurRolloverPolicy.isolate(true, true, true, false));
        assertFalse(WorkspacePassBlurRolloverPolicy.isolate(true, false, true, true));
        assertFalse(WorkspacePassBlurRolloverPolicy.isolate(true, false, false, false));
        assertFalse(WorkspacePassBlurRolloverPolicy.isolate(false, false, true, false));
    }
}
