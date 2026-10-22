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
                Os4LauncherIdentityPolicy.TARGET_RELEASE, 36, false, true));
    }

    @Test public void otherLaunchersAndBuildsFailClosed() {
        String version = Os4LauncherIdentityPolicy.TARGET_RELEASE;
        assertFalse(matches("com.android.launcher3", version, 36, false, true));
        assertFalse(matches("com.miui.home", "RELEASE-8.01.02.7752-260904-10100945-R",
                36, false, true));
        assertFalse(matches("com.miui.home", "RELEASE-8.01.02.7751", 36, false, true));
        assertFalse(matches("com.miui.home", version, 35, false, true));
        assertFalse(matches("com.miui.home", version, 37, false, true));
        assertFalse(matches("com.miui.home", version, 36, true, true));
        assertFalse(matches("com.miui.home", version, 36, false, false));
        assertFalse(matches(null, null, 36, false, true));
    }
}
