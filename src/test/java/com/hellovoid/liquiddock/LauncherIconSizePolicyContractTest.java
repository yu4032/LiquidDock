package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Test;

/** Contract for one shared Launcher icon-size policy across workspace, Dock, and small folders. */
public class LauncherIconSizePolicyContractTest {
    private static Class<?> policy() throws Exception {
        try {
            return Class.forName("com.hellovoid.liquiddock.LauncherIconSizePolicy");
        } catch (ClassNotFoundException e) {
            assertTrue("LauncherIconSizePolicy must exist", false);
            throw e;
        }
    }

    private static float scale(boolean enabled, int percent) throws Exception {
        Method method = policy().getDeclaredMethod("scale", boolean.class, int.class);
        method.setAccessible(true);
        return ((Number) method.invoke(null, enabled, percent)).floatValue();
    }

    private static int scaledPx(int px, boolean enabled, int percent) throws Exception {
        Method method = policy().getDeclaredMethod(
                "scaledPx", int.class, boolean.class, int.class);
        method.setAccessible(true);
        return ((Number) method.invoke(null, px, enabled, percent)).intValue();
    }

    @Test
    public void disabledPolicyIsExactlySystemSize() throws Exception {
        assertEquals(1.0f, scale(false, 120), 0.0001f);
        assertEquals(96, scaledPx(96, false, 120));
    }

    @Test
    public void enabledPolicyUsesPercentAndClampsToSafeRange() throws Exception {
        assertEquals(0.80f, scale(true, 50), 0.0001f);
        assertEquals(1.10f, scale(true, 110), 0.0001f);
        assertEquals(1.20f, scale(true, 150), 0.0001f);
        assertEquals(106, scaledPx(96, true, 110));
    }
}
