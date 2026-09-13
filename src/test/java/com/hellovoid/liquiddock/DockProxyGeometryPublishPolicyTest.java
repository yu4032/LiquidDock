package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.junit.Test;

public class DockProxyGeometryPublishPolicyTest {
    @Test
    public void launchAwayPublishesGeometryBeforeProxyIconIsVisible() throws Exception {
        assertTrue(shouldPublishGeometry(false, false));
    }

    @Test
    public void closeToHomeKeepsHiddenProxyOwnershipUntilVendorProxyIsVisible() throws Exception {
        assertFalse(shouldPublishGeometry(true, false));
        assertTrue(shouldPublishGeometry(true, true));
    }

    private static boolean shouldPublishGeometry(
            boolean closeToHome, boolean proxyVisible) throws Exception {
        Class<?> type = Class.forName(
                "com.hellovoid.liquiddock.LauncherGlassProxyVisibility");
        final Method method;
        try {
            method = type.getDeclaredMethod(
                    "shouldPublishGeometry", boolean.class, boolean.class);
        } catch (NoSuchMethodException missing) {
            fail("LauncherGlassProxyVisibility must define direction-aware geometry publication");
            return false;
        }
        method.setAccessible(true);
        return (Boolean) method.invoke(null, closeToHome, proxyVisible);
    }
}
