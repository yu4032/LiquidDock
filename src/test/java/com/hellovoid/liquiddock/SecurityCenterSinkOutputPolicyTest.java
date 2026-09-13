package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.view.View;

import java.lang.reflect.Method;

import org.junit.Test;

/** Animated material carriers use root-space output so backdrop pixels stay screen-stable. */
public class SecurityCenterSinkOutputPolicyTest {
    @Test
    public void allAnimatedSecurityCenterRolesUseRootSpaceOutput() throws Exception {
        Class<?> policy;
        Class<?> role;
        try {
            policy = Class.forName("com.hellovoid.liquiddock.SecurityCenterSinkOutputPolicy");
            role = Class.forName(
                    "com.hellovoid.liquiddock.SecurityCenterSinkOutputPolicy$MaterialRole");
        } catch (ClassNotFoundException missing) {
            fail("typed sink output policy and semantic material roles are required");
            return;
        }
        Method method = policy.getDeclaredMethod("usesRootSpaceOutput", role);
        method.setAccessible(true);

        @SuppressWarnings({"rawtypes", "unchecked"})
        Object dock = Enum.valueOf((Class<? extends Enum>) role, "DOCK");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object toolbox = Enum.valueOf((Class<? extends Enum>) role, "TOOLBOX");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object allApps = Enum.valueOf((Class<? extends Enum>) role, "ALL_APPS");

        assertTrue("Dock height/scale/radius are animated every frame and must not resize its Surface",
                (Boolean) method.invoke(null, dock));
        assertTrue((Boolean) method.invoke(null, toolbox));
        assertTrue((Boolean) method.invoke(null, allApps));
        assertFalse((Boolean) method.invoke(null, new Object[]{null}));
    }

    @Test
    public void sinkAcceptsSemanticRoleForOutputSpace() throws Exception {
        Class<?> role = Class.forName(
                "com.hellovoid.liquiddock.SecurityCenterSinkOutputPolicy$MaterialRole");
        try {
            SecurityCenterGlassSinkView.class.getDeclaredMethod(
                    "attachBefore", View.class, SecurityCenterGlassSession.class, role);
        } catch (NoSuchMethodException missing) {
            fail("sink attachment must receive the semantic material role explicitly");
        }
    }
}
