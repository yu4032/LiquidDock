package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.ConfigKey;
import com.hellovoid.liquiddock.config.SidebarGlassConfig;

import java.lang.reflect.Method;

import org.junit.Test;

/** Behavioural contract for the SecurityCenter sidebar liquid-glass feature gate. */
public class SidebarGlassPolicyTest {
    @Test public void sidebarConfigExistsAndDefaultsOff() {
        ConfigKey<Boolean> enabled = SidebarGlassConfig.ENABLED;
        assertEquals("sidebar_liquid_glass", enabled.name());
        assertFalse(enabled.uiDefault());
        assertFalse(enabled.runtimeFallback());
    }

    @Test public void onlySecurityCenterWithBothSwitchesEnabledInstalls() throws Exception {
        Class<?> policy = Class.forName("com.hellovoid.liquiddock.SidebarGlassPolicy");
        Method shouldInstall = policy.getDeclaredMethod(
                "shouldInstall", String.class, boolean.class, boolean.class);
        shouldInstall.setAccessible(true);

        assertTrue((Boolean) shouldInstall.invoke(
                null, "com.miui.securitycenter", true, true));
        assertFalse((Boolean) shouldInstall.invoke(
                null, "com.miui.securitycenter", false, true));
        assertFalse((Boolean) shouldInstall.invoke(
                null, "com.miui.securitycenter", true, false));
        assertFalse((Boolean) shouldInstall.invoke(
                null, "com.miui.home", true, true));
        assertFalse((Boolean) shouldInstall.invoke(
                null, "com.android.systemui", true, true));
    }
}
