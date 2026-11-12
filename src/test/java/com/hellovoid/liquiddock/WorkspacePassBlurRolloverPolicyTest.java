package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WorkspacePassBlurRolloverPolicyTest {
    @Test public void destroyedOldWorkspaceCanRecoverOnDistinctLiveViewRoot() {
        assertTrue(WorkspacePassBlurRolloverPolicy.isolate(true, false, true, false, false));
    }
    @Test public void newLayerSupportsRecoveryEvenIfOldRootJavaHandleIsValid() {
        assertTrue(WorkspacePassBlurRolloverPolicy.distinctNativeLayer(5440, 5497));
        assertTrue(WorkspacePassBlurRolloverPolicy.isolate(true, true, true, false, true));
    }
    @Test public void sameOrUnknownLayerCannotBypassCommittedDetach() {
        assertFalse(WorkspacePassBlurRolloverPolicy.distinctNativeLayer(5440, 5440));
        assertFalse(WorkspacePassBlurRolloverPolicy.distinctNativeLayer(-1, 5497));
        assertFalse(WorkspacePassBlurRolloverPolicy.isolate(true, true, true, false, false));
        assertFalse(WorkspacePassBlurRolloverPolicy.isolate(true, true, true, true, true));
        assertFalse(WorkspacePassBlurRolloverPolicy.isolate(true, false, false, false, true));
        assertFalse(WorkspacePassBlurRolloverPolicy.isolate(false, false, true, false, true));
    }
}
