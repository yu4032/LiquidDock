package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class WidgetThemePolicyTest {
    @Test public void forceLightRewritesOnlyNightBits() throws Exception {
        assertEquals(0x02 | 0x10, apply(0x02 | 0x20, "light"));
    }

    @Test public void forceDarkRewritesOnlyNightBits() throws Exception {
        assertEquals(0x03 | 0x20, apply(0x03 | 0x10, "dark"));
    }

    @Test public void autoPreservesOriginalUiMode() throws Exception {
        assertEquals(0x06 | 0x10, apply(0x06 | 0x10, "auto"));
        assertEquals(0x06 | 0x20, apply(0x06 | 0x20, "auto"));
    }

    private static int apply(int uiMode, String mode) throws Exception {
        Class<?> policy = findPolicyClass();
        assertNotNull("WidgetThemePolicy must exist", policy);
        Method apply = policy.getDeclaredMethod("applyToUiMode", int.class, String.class);
        return (Integer) apply.invoke(null, uiMode, mode);
    }

    private static Class<?> findPolicyClass() {
        try {
            return Class.forName("com.hellovoid.liquiddock.WidgetThemePolicy");
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
