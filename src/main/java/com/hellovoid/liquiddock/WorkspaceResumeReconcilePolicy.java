package com.hellovoid.liquiddock;

/** Bound provider Widgets and launcher icons are leaves for HOME resume reconciliation.
 * Their content is updated by dedicated vendor hooks, not by traversing descendants. */
final class WorkspaceResumeReconcilePolicy {
    private WorkspaceResumeReconcilePolicy() {}

    static boolean shouldRebind(boolean alreadyOwnedByLiveRoot) {
        return !alreadyOwnedByLiveRoot;
    }

    static boolean shouldVisitChildren(boolean widgetHost, boolean iconHost) {
        return !widgetHost && !iconHost;
    }
}
