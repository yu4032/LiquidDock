package com.hellovoid.liquiddock;

/** Android-free planner for Security Center glass ownership transitions. */
final class SecurityCenterGlassRuntimeTransitionPolicy {
    static final class Snapshot {
        final boolean coreEnabled;
        final boolean glassEnabled;
        final boolean securityCenterEnabled;

        Snapshot(boolean coreEnabled, boolean glassEnabled, boolean securityCenterEnabled) {
            this.coreEnabled = coreEnabled;
            this.glassEnabled = glassEnabled;
            this.securityCenterEnabled = securityCenterEnabled;
        }

        boolean effective() {
            return coreEnabled && glassEnabled && securityCenterEnabled;
        }
    }

    static final class Transition {
        final boolean releaseAll;

        Transition(boolean releaseAll) {
            this.releaseAll = releaseAll;
        }
    }

    private SecurityCenterGlassRuntimeTransitionPolicy() {}

    static Transition plan(Snapshot before, Snapshot after) {
        return new Transition(before.effective() && !after.effective());
    }
}
