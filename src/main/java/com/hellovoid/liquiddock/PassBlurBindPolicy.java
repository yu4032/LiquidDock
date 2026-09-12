package com.hellovoid.liquiddock;

import java.util.LinkedHashSet;

/** Pure domain policy for native PassBlur binding. */
final class PassBlurBindPolicy {
    private static final String[] SYSTEM_EXCLUSIONS = {
            "NavigationBar",
            "StatusBar",
            "GestureStub"
    };

    private PassBlurBindPolicy() {}

    static float nativeScale(PassBlurDomain domain, float requestedScale) {
        return PassBlurQualityPolicy.bridgeScale(
                domain == PassBlurDomain.LAUNCHER_WORKSPACE,
                Math.round(requestedScale * 100f));
    }

    static boolean requiresUnlockGate(PassBlurDomain domain) {
        return domain == PassBlurDomain.LAUNCHER_WORKSPACE;
    }

    /**
     * Security Center consumes PassBlur on demand. Pausing after each accepted source frame makes
     * the next request a real false -> true compositor update edge instead of a no-op resume while
     * the binding is already marked enabled. Launcher Workspace and Dock retain their established
     * continuous producer behavior.
     */
    static boolean shouldPauseAfterFreshFrame(PassBlurDomain domain) {
        return domain == PassBlurDomain.SECURITY_CENTER;
    }

    static String[] exclusions(String rootSurfaceName, String[] extras) {
        LinkedHashSet<String> exclusions = new LinkedHashSet<>();
        addIfPresent(exclusions, rootSurfaceName);
        for (String systemExclusion : SYSTEM_EXCLUSIONS) {
            exclusions.add(systemExclusion);
        }
        if (extras != null) {
            for (String extra : extras) {
                addIfPresent(exclusions, extra);
            }
        }
        return exclusions.toArray(new String[0]);
    }

    private static void addIfPresent(LinkedHashSet<String> exclusions, String name) {
        if (name != null && !name.isEmpty()) exclusions.add(name);
    }
}
