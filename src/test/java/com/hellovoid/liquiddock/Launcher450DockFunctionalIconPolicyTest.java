package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.ConfigSchema;

import org.junit.Test;

/** Runtime policy tests for Launcher 4.50 functional Dock icon glass. */
public class Launcher450DockFunctionalIconPolicyTest {
    @Test
    public void launcher450FunctionalViewTypesUseVendorAdapterAuthorities() {
        assertTrue(Launcher450DockFunctionalIconPolicy.isFunctionalViewType(2));
        assertTrue(Launcher450DockFunctionalIconPolicy.isFunctionalViewType(512));
        assertTrue(Launcher450DockFunctionalIconPolicy.isFunctionalViewType(1024));
        assertTrue(Launcher450DockFunctionalIconPolicy.isFunctionalViewType(2048));
        assertTrue(Launcher450DockFunctionalIconPolicy.isFunctionalViewType(4096));
        assertTrue(Launcher450DockFunctionalIconPolicy.isFunctionalViewType(16384));

        assertFalse(Launcher450DockFunctionalIconPolicy.isFunctionalViewType(4));
        assertFalse(Launcher450DockFunctionalIconPolicy.isFunctionalViewType(8));
        assertFalse(Launcher450DockFunctionalIconPolicy.isFunctionalViewType(128));
        assertFalse(Launcher450DockFunctionalIconPolicy.isFunctionalViewType(8192));
        assertFalse(Launcher450DockFunctionalIconPolicy.isFunctionalViewType(32768));
        assertFalse(Launcher450DockFunctionalIconPolicy.isFunctionalViewType(65536));
    }

    @Test
    public void fullIconModeAllowsEveryIconButFunctionalOnlyIsDockScoped() {
        assertTrue(Launcher450DockFunctionalIconPolicy.shouldRender(
                true, false, false, false));
        assertTrue(Launcher450DockFunctionalIconPolicy.shouldRender(
                true, true, true, false));

        assertTrue(Launcher450DockFunctionalIconPolicy.shouldRender(
                false, true, true, true));
        assertFalse(Launcher450DockFunctionalIconPolicy.shouldRender(
                false, true, true, false));
        assertFalse(Launcher450DockFunctionalIconPolicy.shouldRender(
                false, true, false, true));
        assertFalse(Launcher450DockFunctionalIconPolicy.shouldRender(
                false, false, true, true));
    }

    @Test
    public void functionalDockGlassIsIndependentAndDefaultOff() {
        assertFalse(ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.uiDefault());
        assertFalse(ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.runtimeFallback());
        assertTrue("liquid_functional_dock_icon_glass".equals(
                ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.name()));
    }
}
