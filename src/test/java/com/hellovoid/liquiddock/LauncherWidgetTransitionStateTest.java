package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.Test;

/** Regression coverage for widget <-> app glass ownership and fresh-frame gating. */
public class LauncherWidgetTransitionStateTest {
    private static Object state() throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.hellovoid.liquiddock.LauncherWidgetTransitionState");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("missing widget transition state", missing);
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

    @Test public void launchFadeRetainsGeometryUntilFadeEnds() throws Exception {
        Object state = state();
        assertFalse((Boolean) call(state, "shouldRetainGeometry", new Class<?>[]{}));

        call(state, "beginLaunchFadeOut", new Class<?>[]{});
        assertTrue((Boolean) call(state, "isLaunchFadeOut", new Class<?>[]{}));
        assertTrue((Boolean) call(state, "shouldRetainGeometry", new Class<?>[]{}));

        call(state, "finishLaunchFadeOut", new Class<?>[]{});
        assertFalse((Boolean) call(state, "isLaunchFadeOut", new Class<?>[]{}));
        assertFalse((Boolean) call(state, "shouldRetainGeometry", new Class<?>[]{}));
    }

    @Test public void widgetReturnWaitsForMatchingFreshGeneration() throws Exception {
        Object state = state();
        call(state, "beginReturnWaitingFresh", new Class<?>[]{long.class}, 42L);

        assertTrue((Boolean) call(state, "isReturnWaitingFresh", new Class<?>[]{}));
        assertTrue((Boolean) call(state, "shouldRetainGeometry", new Class<?>[]{}));
        assertFalse((Boolean) call(state, "onFreshFrame", new Class<?>[]{long.class}, 41L));
        assertTrue((Boolean) call(state, "isReturnWaitingFresh", new Class<?>[]{}));

        assertTrue((Boolean) call(state, "onFreshFrame", new Class<?>[]{long.class}, 42L));
        assertTrue((Boolean) call(state, "isReturnFadeIn", new Class<?>[]{}));
        assertTrue((Boolean) call(state, "shouldRetainGeometry", new Class<?>[]{}));

        // The same fresh frame must not start a second fade.
        assertFalse((Boolean) call(state, "onFreshFrame", new Class<?>[]{long.class}, 42L));
        call(state, "finishReturnFadeIn", new Class<?>[]{});
        assertFalse((Boolean) call(state, "shouldRetainGeometry", new Class<?>[]{}));
    }

    @Test public void newerReturnSupersedesOlderPendingGeneration() throws Exception {
        Object state = state();
        call(state, "beginReturnWaitingFresh", new Class<?>[]{long.class}, 7L);
        call(state, "beginReturnWaitingFresh", new Class<?>[]{long.class}, 9L);

        assertFalse((Boolean) call(state, "onFreshFrame", new Class<?>[]{long.class}, 7L));
        assertTrue((Boolean) call(state, "onFreshFrame", new Class<?>[]{long.class}, 9L));
    }
}
