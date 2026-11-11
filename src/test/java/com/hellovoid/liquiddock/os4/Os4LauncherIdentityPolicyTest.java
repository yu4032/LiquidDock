package com.hellovoid.liquiddock.os4;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class Os4LauncherIdentityPolicyTest {
    private static boolean matches(String pkg, boolean hasCode, boolean arm64) {
        return Os4LauncherIdentityPolicy.matchesAotPublisher(pkg, hasCode, arm64);
    }

    @Test public void anyAotLauncherReleaseIsEligibleForPublishing() {
        assertTrue(matches("com.miui.home", false, true));
    }

    @Test public void packageAotAndArchitectureStillDefinePublisherEligibility() {
        assertFalse(matches("com.android.launcher3", false, true));
        assertFalse(matches("com.miui.home", true, true));
        assertFalse(matches("com.miui.home", false, false));
        assertFalse(matches(null, false, true));
    }

    @Test public void legacyJavaBackendIsBlockedForEveryAotLauncher() {
        assertTrue(Os4LauncherIdentityPolicy.shouldBlockLegacyJavaBackend(false));
        assertFalse(Os4LauncherIdentityPolicy.shouldBlockLegacyJavaBackend(true));
    }
}
