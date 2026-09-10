package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SecurityCenterHookSpecTest {
    @Test public void resolvesOnlySupportedVersionCode() {
        assertNull(SecurityCenterHookSpec.forVersionCode(40011319L));
        assertNull(SecurityCenterHookSpec.forVersionCode(40011321L));
        assertNull(SecurityCenterHookSpec.forVersionCode(0L));
    }

    @Test public void supportedVersionExposesExactCompatibilityContract() {
        SecurityCenterHookSpec spec = SecurityCenterHookSpec.forVersionCode(40011320L);

        assertEquals("com.miui.gamebooster.service.DockWindowManagerService",
                SecurityCenterHookSpec.BOOTSTRAP_SERVICE_CLASS);
        assertEquals(40011320L, spec.versionCode());
        assertEquals(SecurityCenterVendorGeneration.OS4_SOFT_LIGHT_CAPABLE,
                spec.vendorGeneration());
        assertEquals("com.miui.gamebooster.windowmanager.newbox.TurboLayout", spec.turboLayoutClass());
        assertEquals("com.miui.dock.sidebar.p", spec.sidebarWrapperClass());
        assertEquals("ja.a", spec.dockWindowTypeClass());
        assertEquals("M", spec.prepareDockMethod());
        assertEquals("f", spec.type4PredicateMethod());
        assertEquals("getDockLayout", spec.dockLayoutGetter());
        assertEquals("getAppsLayout", spec.appsLayoutGetter());
        assertEquals("d0", spec.toggleAllAppsMethod());
        assertEquals("U", spec.finalBackgroundMethod());
        assertEquals("f17941q", spec.allAppsPresentField());
        assertEquals("f17943s", spec.transformingField());
        assertEquals("gq.g", spec.os4MaterialHelperClass());
        assertEquals("l", spec.os4MaterialResetMethod());
    }

    @Test public void toggleAuthorityUsesVendorTransformationStateInsteadOfTime() {
        assertFalse(SecurityCenterGlassHook.ToggleAuthority.shouldStartTransition(true));
        assertTrue(SecurityCenterGlassHook.ToggleAuthority.shouldStartTransition(false));

        assertTrue(SecurityCenterGlassHook.ToggleAuthority.shouldKeepWaiting(true));
        assertFalse(SecurityCenterGlassHook.ToggleAuthority.shouldKeepWaiting(false));
    }
}
