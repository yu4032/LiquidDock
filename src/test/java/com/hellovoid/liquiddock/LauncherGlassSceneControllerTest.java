package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.Test;

/** Pure behavior coverage for the Workspace scene visibility/freshness state machine. */
public class LauncherGlassSceneControllerTest {
    private static Object machine() throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.hellovoid.liquiddock.LauncherGlassSceneController$StateMachine");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("missing LauncherGlassSceneController.StateMachine", missing);
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

    private static long generation(Object state) throws Exception {
        return (Long) call(state, "generation", new Class<?>[0]);
    }

    private static boolean visible(Object state) throws Exception {
        return (Boolean) call(state, "isLayerVisible", new Class<?>[0]);
    }

    @Test public void coveredSceneRequiresFreshFrameBeforeReveal() throws Exception {
        Object state = machine();
        call(state, "onRootReady", new Class<?>[0]);
        long first = generation(state);
        assertFalse(visible(state));
        call(state, "onFreshFrameReady", new Class<?>[]{long.class}, first);
        assertTrue(visible(state));

        call(state, "setCovered", new Class<?>[]{boolean.class}, true);
        assertFalse(visible(state));
        call(state, "setCovered", new Class<?>[]{boolean.class}, false);
        assertFalse(visible(state));
        call(state, "onFreshFrameReady", new Class<?>[]{long.class}, generation(state));
        assertTrue(visible(state));
    }

    @Test public void staleGenerationCannotRevealLayer() throws Exception {
        Object state = machine();
        call(state, "onRootReady", new Class<?>[0]);
        long stale = generation(state);
        call(state, "onGenerationInvalidated", new Class<?>[0]);
        assertTrue(generation(state) > stale);
        call(state, "onFreshFrameReady", new Class<?>[]{long.class}, stale);
        assertFalse(visible(state));
        call(state, "onFreshFrameReady", new Class<?>[]{long.class}, generation(state));
        assertTrue(visible(state));
    }

    @Test public void onlyFirstFreshFrameAfterCoverageRequestsFadeReveal() throws Exception {
        Object state = machine();
        call(state, "onRootReady", new Class<?>[0]);
        call(state, "onFreshFrameReady", new Class<?>[]{long.class}, generation(state));
        assertFalse((Boolean) call(state, "consumeFadeReveal", new Class<?>[0]));

        call(state, "setCovered", new Class<?>[]{boolean.class}, true);
        call(state, "setCovered", new Class<?>[]{boolean.class}, false);
        call(state, "onFreshFrameReady", new Class<?>[]{long.class}, generation(state));
        assertTrue((Boolean) call(state, "consumeFadeReveal", new Class<?>[0]));
        assertFalse((Boolean) call(state, "consumeFadeReveal", new Class<?>[0]));

        call(state, "onGenerationInvalidated", new Class<?>[0]);
        call(state, "onFreshFrameReady", new Class<?>[]{long.class}, generation(state));
        assertFalse((Boolean) call(state, "consumeFadeReveal", new Class<?>[0]));
    }
}
