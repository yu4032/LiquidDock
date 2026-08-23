package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class WidgetThemePolicyTest {
    @Test public void forceLightRewritesOnlyNightBits() throws Exception {
        Class<?> policy = findPolicyClass();
        assertNotNull("WidgetThemePolicy must exist", policy);
        Method apply = policy.getDeclaredMethod("applyToUiMode", int.class, String.class);

        int deskDark = 0x02 | 0x20;
        int result = (Integer) apply.invoke(null, deskDark, "light");

        assertEquals(0x02 | 0x10, result);
    }

    private static Class<?> findPolicyClass() {
        try {
            return Class.forName("com.hellovoid.liquiddock.WidgetThemePolicy");
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
