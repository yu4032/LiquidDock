package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.ConfigKey;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Test;

/** Behavioural contract for the SecurityCenter sidebar liquid-glass feature gate. */
public class SidebarGlassPolicyTest {
    @Test public void sidebarConfigExistsAndDefaultsOff() throws Exception {
        Class<?> sidebar = Class.forName("com.hellovoid.liquiddock.config.ConfigSchema$Sidebar");
        Field enabledField = sidebar.getDeclaredField("ENABLED");
        Object raw = enabledField.get(null);
        assertTrue(raw instanceof ConfigKey<?>);
        @SuppressWarnings("unchecked")
        ConfigKey<Boolean> enabled = (ConfigKey<Boolean>) raw;
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
