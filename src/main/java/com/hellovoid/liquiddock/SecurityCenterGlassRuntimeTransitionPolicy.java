package com.hellovoid.liquiddock;

/** Android-free planner for Security Center glass ownership transitions. */
final class SecurityCenterGlassRuntimeTransitionPolicy {
    private static final int ASSISTANT_GAME = 1;
    private static final int ASSISTANT_VIDEO = 3;
    private static final int ASSISTANT_GLOBAL_DOCK = 4;

    enum AssistantBackend {
        IGNORE,
        VENDOR_ONLY,
        CUSTOM_SHADER,
        FRAMEWORK_PASS_WINDOW
    }

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

    static final class AssistantTransition {
        final boolean bindCustomGlass;
        final boolean releaseExistingCustomGlass;
        final boolean requiresDeferredPrepare;
        final boolean rearmOnSidebarShow;
        final AssistantBackend backend;

        AssistantTransition(
                boolean bindCustomGlass,
                boolean releaseExistingCustomGlass,
                boolean requiresDeferredPrepare,
                boolean rearmOnSidebarShow,
                AssistantBackend backend) {
            this.bindCustomGlass = bindCustomGlass;
            this.releaseExistingCustomGlass = releaseExistingCustomGlass;
            this.requiresDeferredPrepare = requiresDeferredPrepare;
            this.rearmOnSidebarShow = rearmOnSidebarShow;
            this.backend = backend;
        }
    }

    private SecurityCenterGlassRuntimeTransitionPolicy() {}

    static Transition plan(Snapshot before, Snapshot after) {
        return new Transition(before.effective() && !after.effective());
    }

    static AssistantTransition planAssistant(int type) {
        if (type == ASSISTANT_GAME) {
            return new AssistantTransition(
                    false, true, false, false, AssistantBackend.VENDOR_ONLY);
        }
        if (type == ASSISTANT_VIDEO || type == ASSISTANT_GLOBAL_DOCK) {
            return new AssistantTransition(
                    true, false, true, true, AssistantBackend.CUSTOM_SHADER);
        }
        return new AssistantTransition(false, false, false, false, AssistantBackend.IGNORE);
    }
}
