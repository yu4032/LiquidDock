package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.Test;

/** Typed contract for the decompiled 40011320 Global Dock / All Apps lifecycle. */
public class SecurityCenterHookLifecycleContractTest {
    private static boolean hasMethod(Class<?> type, String name, int parameterCount) {
        return Arrays.stream(type.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals(name)
                        && method.getParameterCount() == parameterCount);
    }

    private static Object invokeNoArg(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    @Test
    public void globalDockContractExposesConfigureAndDockReadyMembers() throws Exception {
        SecurityCenterHookSpec spec = SecurityCenterHookSpec.forVersionCode(40011320L);

        assertTrue(hasMethod(SecurityCenterHookSpec.class, "configureDockMethod", 0));
        assertTrue(hasMethod(SecurityCenterHookSpec.class, "dockReadyMethod", 0));
        assertTrue("V".equals(invokeNoArg(spec, "configureDockMethod")));
        assertTrue("c0".equals(invokeNoArg(spec, "dockReadyMethod")));
    }

    @Test
    public void allAppsIsLateBoundInsteadOfRequiredForInitialDockSession() {
        assertTrue(hasMethod(SecurityCenterGlassCoordinator.class, "bindGlobalDock", 2));
        assertTrue(hasMethod(SecurityCenterGlassCoordinator.class, "updateAllAppsLayout", 2));
    }
}
