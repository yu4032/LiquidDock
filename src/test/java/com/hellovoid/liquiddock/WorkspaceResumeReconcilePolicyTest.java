package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class WorkspaceResumeReconcilePolicyTest {
    @Test public void alreadyOwnedHostsDoNotRepeatExpensiveProviderDiscovery() {
        assertFalse(WorkspaceResumeReconcilePolicy.shouldRebind(true));
        assertTrue(WorkspaceResumeReconcilePolicy.shouldRebind(false));
    }

    @Test public void widgetProviderAndIconSubtreesAreTerminal() {
        assertFalse(WorkspaceResumeReconcilePolicy.shouldVisitChildren(true, false));
        assertFalse(WorkspaceResumeReconcilePolicy.shouldVisitChildren(false, true));
        assertFalse(WorkspaceResumeReconcilePolicy.shouldVisitChildren(true, true));
        assertTrue(WorkspaceResumeReconcilePolicy.shouldVisitChildren(false, false));
    }
}
