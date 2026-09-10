package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SecurityCenterHookSpecTest {
    @Test public void resolvesOnlySupportedVersionCode() {
        assertNull(SecurityCenterHookSpec.forVersionCode(40011319L));
        assertNull(SecurityCenterHookSpec.forVersionCode(40011321L));
        assertNull(SecurityCenterHookSpec.forVersionCode(40011354L));
        assertNull(SecurityCenterHookSpec.forVersionCode(40011356L));
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
        assertEquals("ob.e0", spec.dockWindowManagerClass());
        assertEquals("ja.a", spec.dockWindowTypeClass());
        assertEquals("V", spec.configureDockMethod());
        assertEquals("c0", spec.dockReadyMethod());
        assertEquals("g", spec.gameToolboxPredicateMethod());
        assertEquals("k", spec.videoToolboxPredicateMethod());
        assertEquals("f", spec.globalDockPredicateMethod());
        assertEquals("f", spec.type4PredicateMethod());
        assertEquals("C", spec.sidebarTurboGetter());
        assertEquals("getDockLayout", spec.dockLayoutGetter());
        assertEquals("getAppsLayout", spec.appsLayoutGetter());
        assertEquals("getBoxView", spec.boxViewGetter());
        assertEquals("com.miui.gamebooster.windowmanager.newbox.x1", spec.gameToolboxViewClass());
        assertEquals("getMainView", spec.gameToolboxMaterialGetter());
        assertEquals("o", spec.gameToolboxMaterialRestoreMethod());
        assertEquals("game_toolbox_background_radius", spec.gameToolboxCornerRadiusResource());
        assertEquals("za.p", spec.videoToolboxAdapterClass());
        assertEquals("getVideoBoxViewAdapter", spec.videoToolboxAdapterGetter());
        assertEquals("t", spec.videoToolboxMaterialRestoreMethod());
        assertEquals("main_content", spec.videoToolboxMaterialViewIdResource());
        assertEquals("d0", spec.toggleAllAppsMethod());
        assertEquals("d2", spec.removeTurboLayoutMethod());
        assertEquals("f2", spec.removeTurboLayoutWithoutAnimationMethod());
        assertEquals("U", spec.finalBackgroundMethod());
        assertEquals("q", spec.allAppsPresentField());
        assertEquals("s", spec.transformingField());
        assertEquals("gq.g", spec.os4MaterialHelperClass());
        assertEquals("l", spec.os4MaterialResetMethod());
        assertEquals("dp_24", spec.allAppsCornerRadiusResource());
    }

    @Test public void currentDeviceBuild40011355UsesSameValidatedMemberContract() {
        SecurityCenterHookSpec spec = SecurityCenterHookSpec.forVersionCode(40011355L);

        assertNotNull(spec);
        assertEquals(40011355L, spec.versionCode());
        assertEquals(SecurityCenterVendorGeneration.OS4_SOFT_LIGHT_CAPABLE,
                spec.vendorGeneration());
        assertEquals("com.miui.gamebooster.windowmanager.newbox.TurboLayout", spec.turboLayoutClass());
        assertEquals("com.miui.dock.sidebar.p", spec.sidebarWrapperClass());
        assertEquals("ob.e0", spec.dockWindowManagerClass());
        assertEquals("ja.a", spec.dockWindowTypeClass());
        assertEquals("V", spec.configureDockMethod());
        assertEquals("c0", spec.dockReadyMethod());
        assertEquals("g", spec.gameToolboxPredicateMethod());
        assertEquals("k", spec.videoToolboxPredicateMethod());
        assertEquals("f", spec.globalDockPredicateMethod());
        assertEquals("f", spec.type4PredicateMethod());
        assertEquals("C", spec.sidebarTurboGetter());
        assertEquals("getDockLayout", spec.dockLayoutGetter());
        assertEquals("getAppsLayout", spec.appsLayoutGetter());
        assertEquals("getBoxView", spec.boxViewGetter());
        assertEquals("com.miui.gamebooster.windowmanager.newbox.x1", spec.gameToolboxViewClass());
        assertEquals("getMainView", spec.gameToolboxMaterialGetter());
        assertEquals("o", spec.gameToolboxMaterialRestoreMethod());
        assertEquals("game_toolbox_background_radius", spec.gameToolboxCornerRadiusResource());
        assertEquals("za.p", spec.videoToolboxAdapterClass());
        assertEquals("getVideoBoxViewAdapter", spec.videoToolboxAdapterGetter());
        assertEquals("t", spec.videoToolboxMaterialRestoreMethod());
        assertEquals("main_content", spec.videoToolboxMaterialViewIdResource());
        assertEquals("d0", spec.toggleAllAppsMethod());
        assertEquals("d2", spec.removeTurboLayoutMethod());
        assertEquals("f2", spec.removeTurboLayoutWithoutAnimationMethod());
        assertEquals("U", spec.finalBackgroundMethod());
        assertEquals("q", spec.allAppsPresentField());
        assertEquals("s", spec.transformingField());
        assertEquals("gq.g", spec.os4MaterialHelperClass());
        assertEquals("l", spec.os4MaterialResetMethod());
        assertEquals("dp_24", spec.allAppsCornerRadiusResource());
    }

    @Test public void resourceTableSymbolIsNotRuntimeDexClassContract() {
        for (Method method : SecurityCenterHookSpec.class.getDeclaredMethods()) {
            assertFalse("JADX resource symbols must not become runtime class contracts",
                    "resourceDimenClass".equals(method.getName()));
        }
    }

    @Test public void toggleAuthorityUsesVendorTransformationStateInsteadOfTime() {
        assertFalse(SecurityCenterGlassHook.ToggleAuthority.shouldStartTransition(true));
        assertTrue(SecurityCenterGlassHook.ToggleAuthority.shouldStartTransition(false));

        assertTrue(SecurityCenterGlassHook.ToggleAuthority.shouldKeepWaiting(true));
        assertFalse(SecurityCenterGlassHook.ToggleAuthority.shouldKeepWaiting(false));
    }
}
