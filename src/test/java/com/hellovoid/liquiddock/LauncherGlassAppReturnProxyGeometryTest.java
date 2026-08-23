package com.hellovoid.liquiddock;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression coverage for Launcher 4.50 App->HOME visual-owner geometry handoff. */
public class LauncherGlassAppReturnProxyGeometryTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + name));
    }

    private static Object state() throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.hellovoid.liquiddock.LauncherGlassVisualOwnerState");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("missing visual-owner state", missing);
        }
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    @Test public void launchProxyOwnsGeometryUntilVendorReturnsSourceView() throws Exception {
        Object state = state();
        assertFalse((Boolean) call(state, "isLaunchProxyActive", new Class<?>[]{}));
        assertNull(call(state, "copyLaunchProxyRect", new Class<?>[]{}));

        assertTrue((Boolean) call(state, "beginLaunchProxy", new Class<?>[]{}));
        assertTrue((Boolean) call(state, "isLaunchProxyActive", new Class<?>[]{}));
        assertNull(call(state, "copyLaunchProxyRect", new Class<?>[]{}));

        float[] first = new float[]{20f, 30f, 220f, 230f};
        assertTrue((Boolean) call(state, "updateLaunchProxyRect",
                new Class<?>[]{float[].class}, new Object[]{first}));
        assertArrayEquals(first, (float[]) call(state, "copyLaunchProxyRect", new Class<?>[]{}), 0f);

        assertFalse((Boolean) call(state, "updateLaunchProxyRect",
                new Class<?>[]{float[].class}, new Object[]{first.clone()}));
        float[] moved = new float[]{40f, 50f, 140f, 150f};
        assertTrue((Boolean) call(state, "updateLaunchProxyRect",
                new Class<?>[]{float[].class}, new Object[]{moved}));
        assertArrayEquals(moved, (float[]) call(state, "copyLaunchProxyRect", new Class<?>[]{}), 0f);

        assertTrue((Boolean) call(state, "endLaunchProxy", new Class<?>[]{}));
        assertFalse((Boolean) call(state, "isLaunchProxyActive", new Class<?>[]{}));
        assertNull(call(state, "copyLaunchProxyRect", new Class<?>[]{}));
    }

    @Test public void staticNodeUsesProxyRectInsteadOfSourceViewWhileProxyOwnsVisual() throws Exception {
        String node = source("LauncherGlassStaticNode.java");
        assertTrue(node.contains("LauncherGlassVisualOwnerState"));
        assertTrue(node.contains("beginLaunchProxy"));
        assertTrue(node.contains("updateLaunchProxyGeometry"));
        assertTrue(node.contains("endLaunchProxy"));
        assertTrue(node.contains("copyLaunchProxyRect"));
        assertFalse(node.contains("suppressedByLaunchProxy"));
    }

    @Test public void windowElementPublishesItsAlreadyCorrectedCurrentRect() throws Exception {
        String hook = source("MiuixLauncherStaticGlassHook.java");
        assertTrue(hook.contains("com.miui.home.recents.anim.WindowElement"));
        assertTrue(hook.contains("\"updateTaskView\""));
        assertTrue(hook.contains("getLauncherTargetView"));
        assertTrue(hook.contains("updateLaunchProxyGeometry"));
        assertTrue(hook.contains("RectF.class, float.class"));
    }

    @Test public void vendorVisibilityOnlyStartsAndEndsOwnerHandoff() throws Exception {
        String hook = source("MiuixLauncherStaticGlassHook.java");
        assertTrue(hook.contains("node.beginLaunchProxy()"));
        assertTrue(hook.contains("node.endLaunchProxy()"));
        assertFalse(hook.contains("setSuppressedByLaunchProxy"));
    }
}
