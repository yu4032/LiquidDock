package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SecurityCenterProcessPolicyTest {
    @Test public void installsOnlyForSecurityCenterUiProcess() {
        assertTrue(SecurityCenterProcessPolicy.shouldInstall(
                "com.miui.securitycenter", "com.miui.securitycenter:ui"));
    }

    @Test public void rejectsOtherSecurityCenterProcessesAndPackages() {
        assertFalse(SecurityCenterProcessPolicy.shouldInstall(
                "com.miui.securitycenter", "com.miui.securitycenter"));
        assertFalse(SecurityCenterProcessPolicy.shouldInstall(
                "com.miui.securitycenter", "com.miui.securitycenter:remote"));
        assertFalse(SecurityCenterProcessPolicy.shouldInstall(
                "com.miui.home", "com.miui.securitycenter:ui"));
    }

    @Test public void rejectsNullAndEmptyPackageOrProcessNames() {
        assertFalse(SecurityCenterProcessPolicy.shouldInstall(null, "com.miui.securitycenter:ui"));
        assertFalse(SecurityCenterProcessPolicy.shouldInstall("", "com.miui.securitycenter:ui"));
        assertFalse(SecurityCenterProcessPolicy.shouldInstall("com.miui.securitycenter", null));
        assertFalse(SecurityCenterProcessPolicy.shouldInstall("com.miui.securitycenter", ""));
    }
}
