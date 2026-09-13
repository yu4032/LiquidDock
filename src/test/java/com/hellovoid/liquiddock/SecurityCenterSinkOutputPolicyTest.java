package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.junit.Test;

/** Root-space output is reserved for the animated All Apps material carrier. */
public class SecurityCenterSinkOutputPolicyTest {
    @Test
    public void onlyAllAppsUsesRootSpaceOutput() throws Exception {
        Class<?> policy;
        try {
            policy = Class.forName("com.hellovoid.liquiddock.SecurityCenterSinkOutputPolicy");
        } catch (ClassNotFoundException missing) {
            fail("SecurityCenterSinkOutputPolicy is required to make output-space selection typed");
            return;
        }
        Method method = policy.getDeclaredMethod("usesRootSpaceOutput", String.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(null, "com.miui.dock.allapps.w"));
        assertTrue((Boolean) method.invoke(null, "com.miui.dock.allapps.SomeFutureCarrier"));
        assertFalse((Boolean) method.invoke(null,
                "com.miui.gamebooster.windowmanager.newbox.y1"));
        assertFalse((Boolean) method.invoke(null,
                "com.miui.gamebooster.windowmanager.newbox.o0"));
        assertFalse((Boolean) method.invoke(null, (Object) null));
    }
}
