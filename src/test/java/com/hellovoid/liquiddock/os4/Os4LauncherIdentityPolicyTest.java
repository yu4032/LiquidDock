package com.hellovoid.liquiddock.os4;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class Os4LauncherIdentityPolicyTest {
    private static boolean matches(String pkg, String ver, int sdk,
                                   boolean hasCode, boolean arm64) {
        return Os4LauncherIdentityPolicy.matches(pkg, ver, sdk, hasCode, arm64);
    }

    @Test public void knownProfileIsEligibleOnlyForFurtherVerification() {
        assertTrue(matches("com.miui.home",
                Os4LauncherIdentityPolicy.TARGET_RELEASE,
                Os4LauncherIdentityPolicy.TARGET_SDK, false, true));
    }

    @Test public void otherLaunchersAndBuildsFailClosed() {
        String version = Os4LauncherIdentityPolicy.TARGET_RELEASE;
        int sdk = Os4LauncherIdentityPolicy.TARGET_SDK;
        assertFalse(matches("com.android.launcher3", version, sdk, false, true));
        assertFalse(matches("com.miui.home",
                "RELEASE-8.01.02.7751-260904-10100945-R", sdk, false, true));
        assertFalse(matches("com.miui.home", "RELEASE-8.01.02.8583", sdk, false, true));
        assertFalse(matches("com.miui.home", version, sdk - 1, false, true));
        assertFalse(matches("com.miui.home", version, sdk + 1, false, true));
        assertFalse(matches("com.miui.home", version, sdk, true, true));
        assertFalse(matches("com.miui.home", version, sdk, false, false));
        assertFalse(matches(null, null, sdk, false, true));
    }

    @Test public void legacyJavaBackendIsBlockedForEveryAotOs4Launcher() {
        assertTrue(Os4LauncherIdentityPolicy.shouldBlockLegacyJavaBackend(36, false));
        assertTrue(Os4LauncherIdentityPolicy.shouldBlockLegacyJavaBackend(37, false));
        assertFalse(Os4LauncherIdentityPolicy.shouldBlockLegacyJavaBackend(35, false));
        assertFalse(Os4LauncherIdentityPolicy.shouldBlockLegacyJavaBackend(37, true));
    }
}
