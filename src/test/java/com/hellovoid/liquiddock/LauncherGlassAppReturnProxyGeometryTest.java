package com.hellovoid.liquiddock;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.Test;

/** Regression coverage for Launcher 4.50 App->HOME visual-owner geometry handoff. */
public class LauncherGlassAppReturnProxyGeometryTest {
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

    @Test public void finalProxyRectAcquiresOwnerWithoutVisibilityBegin() throws Exception {
        Object state = state();
        assertFalse((Boolean) call(state, "isLaunchProxyActive", new Class<?>[]{}));
        assertNull(call(state, "copyLaunchProxyRect", new Class<?>[]{}));

        float[] first = new float[]{20f, 30f, 220f, 230f};
        assertTrue((Boolean) call(state, "updateLaunchProxyRect",
                new Class<?>[]{float[].class}, new Object[]{first}));
        assertTrue((Boolean) call(state, "isLaunchProxyActive", new Class<?>[]{}));
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

    @Test public void hiddenVendorProxyOwnsVisualWithoutPublishingTaskSizedGeometry()
            throws Exception {
        Object state = state();
        assertTrue((Boolean) call(state, "holdLaunchProxyHidden", new Class<?>[]{}));
        assertTrue((Boolean) call(state, "isLaunchProxyActive", new Class<?>[]{}));
        assertNull(call(state, "copyLaunchProxyRect", new Class<?>[]{}));

        // Repeated invisible frames are stable and do not dirty the scene again.
        assertFalse((Boolean) call(state, "holdLaunchProxyHidden", new Class<?>[]{}));

        // The first vendor-visible proxy frame replaces hidden ownership with real geometry.
        float[] visible = new float[]{800f, 500f, 1200f, 900f};
        assertTrue((Boolean) call(state, "updateLaunchProxyRect",
                new Class<?>[]{float[].class}, new Object[]{visible}));
        assertArrayEquals(visible,
                (float[]) call(state, "copyLaunchProxyRect", new Class<?>[]{}), 0f);

        // If vendor hides the proxy again, stale geometry must be cleared immediately.
        assertTrue((Boolean) call(state, "holdLaunchProxyHidden", new Class<?>[]{}));
        assertNull(call(state, "copyLaunchProxyRect", new Class<?>[]{}));
    }

    @Test public void vendorProxyVisibilityMatchesLauncher450ConsumerSemantics() throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.hellovoid.liquiddock.LauncherGlassProxyVisibility");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("missing vendor proxy visibility helper", missing);
        }
        Method view = type.getDeclaredMethod("isView2Visible", float.class, boolean.class);
        Method layer = type.getDeclaredMethod("isLayer2Visible", float.class, boolean.class);
        view.setAccessible(true);
        layer.setAccessible(true);

        // FloatingIconView2.java: setAlpha(f > 0.1f ? 1 : 0).
        assertFalse((Boolean) view.invoke(null, 0f, true));
        assertFalse((Boolean) view.invoke(null, 0.1f, true));
        assertTrue((Boolean) view.invoke(null, 0.1001f, true));
        assertFalse((Boolean) view.invoke(null, 1f, false));

        // FloatingIconLayer2.java: SurfaceControl is shown only for alpha > 0.
        assertFalse((Boolean) layer.invoke(null, 0f, true));
        assertTrue((Boolean) layer.invoke(null, 0.0001f, true));
        assertFalse((Boolean) layer.invoke(null, 1f, false));
    }

}
