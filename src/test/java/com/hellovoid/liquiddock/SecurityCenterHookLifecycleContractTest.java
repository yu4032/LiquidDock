package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.Test;

/** Typed contract for the decompiled 40011320 assistant / All Apps lifecycle. */
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
    public void assistantContractExposesExactTypeAndBoxMembers() throws Exception {
        SecurityCenterHookSpec spec = SecurityCenterHookSpec.forVersionCode(40011320L);

        assertTrue(hasMethod(SecurityCenterHookSpec.class, "configureDockMethod", 0));
        assertTrue(hasMethod(SecurityCenterHookSpec.class, "dockReadyMethod", 0));
        assertTrue(hasMethod(SecurityCenterHookSpec.class, "gameToolboxPredicateMethod", 0));
        assertTrue(hasMethod(SecurityCenterHookSpec.class, "videoToolboxPredicateMethod", 0));
        assertTrue(hasMethod(SecurityCenterHookSpec.class, "globalDockPredicateMethod", 0));
        assertTrue(hasMethod(SecurityCenterHookSpec.class, "boxViewGetter", 0));
        assertEquals("V", invokeNoArg(spec, "configureDockMethod"));
        assertEquals("c0", invokeNoArg(spec, "dockReadyMethod"));
        assertEquals("g", invokeNoArg(spec, "gameToolboxPredicateMethod"));
        assertEquals("k", invokeNoArg(spec, "videoToolboxPredicateMethod"));
        assertEquals("f", invokeNoArg(spec, "globalDockPredicateMethod"));
        assertEquals("getBoxView", invokeNoArg(spec, "boxViewGetter"));
    }

    @Test
    public void dockBoxAndAllAppsAreLateBoundIntoOneCoordinator() {
        assertTrue(hasMethod(SecurityCenterGlassCoordinator.class, "bindAssistant", 4));
        assertTrue(hasMethod(SecurityCenterGlassCoordinator.class, "updateAllAppsLayout", 2));
        assertTrue(hasMethod(SecurityCenterGlassCoordinator.class, "refreshTransitionFrame", 1));
    }

    @Test
    public void transformedVisualBoundsTrackVendorFolmeScaleAndTranslation() throws Exception {
        Class<?> bounds = Class.forName(
                "com.hellovoid.liquiddock.SecurityCenterTransformedBounds");
        Method resolve = bounds.getDeclaredMethod(
                "resolve", float.class, float.class, float.class, float.class,
                float.class, float.class, float.class, float.class,
                float.class, float.class);
        resolve.setAccessible(true);
        Object value = resolve.invoke(null,
                100f, 200f, 400f, 200f,
                200f, 100f, 0.5f, 0.25f,
                30f, -20f);
        Field left = bounds.getDeclaredField("left");
        Field top = bounds.getDeclaredField("top");
        Field right = bounds.getDeclaredField("right");
        Field bottom = bounds.getDeclaredField("bottom");
        left.setAccessible(true);
        top.setAccessible(true);
        right.setAccessible(true);
        bottom.setAccessible(true);
        assertEquals(230f, left.getFloat(value), 0.001f);
        assertEquals(255f, top.getFloat(value), 0.001f);
        assertEquals(430f, right.getFloat(value), 0.001f);
        assertEquals(305f, bottom.getFloat(value), 0.001f);
    }
}
