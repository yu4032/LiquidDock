package com.hellovoid.liquiddock;

import java.util.LinkedHashSet;

/** Pure domain policy for native PassBlur binding. */
final class PassBlurBindPolicy {
    private static final float SECURITY_CENTER_NATIVE_SCALE = 0.25f;

    private static final String[] SYSTEM_EXCLUSIONS = {
            "NavigationBar",
            "StatusBar",
            "GestureStub"
    };

    private PassBlurBindPolicy() {}

    static float nativeScale(PassBlurDomain domain, float requestedScale) {
        // Security Center's vendor PassBlur producer is natively configured at 0.25. Device logs
        // show that changing the same root to 1.0 stops RenderEngine backdrop production even
        // while updateTextureFlag remains true. Treat this as a producer protocol contract rather
        // than a quality knob. Launcher/Dock keep their existing 1.0 bridge policy.
        if (domain == PassBlurDomain.SECURITY_CENTER) {
            return SECURITY_CENTER_NATIVE_SCALE;
        }
        return PassBlurQualityPolicy.bridgeScale(
                domain == PassBlurDomain.LAUNCHER_WORKSPACE,
                Math.round(requestedScale * 100f));
    }

    static boolean requiresUnlockGate(PassBlurDomain domain) {
        return domain == PassBlurDomain.LAUNCHER_WORKSPACE;
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
