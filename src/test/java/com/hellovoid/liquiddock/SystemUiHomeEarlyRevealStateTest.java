package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.Test;

/** HOME presentation may reveal the cached static layer while fresh wallpaper capture stays blocked. */
public class SystemUiHomeEarlyRevealStateTest {
    private static Object machine() throws Exception {
        Class<?> type = Class.forName(
                "com.hellovoid.liquiddock.LauncherGlassSceneController$StateMachine");
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

    private static boolean visible(Object state) throws Exception {
        return (Boolean) call(state, "isLayerVisible", new Class<?>[0]);
    }

    private static long generation(Object state) throws Exception {
        return (Long) call(state, "generation", new Class<?>[0]);
    }

    @Test public void homeStartCanFadeCachedLayerBeforeFreshFrame() throws Exception {
        Object state = machine();
        call(state, "onRootReady", new Class<?>[0]);
        call(state, "onFreshFrameReady", new Class<?>[]{long.class}, generation(state));
        assertTrue(visible(state));

        call(state, "onGenerationInvalidated", new Class<?>[0]);
        assertFalse(visible(state));

        call(state, "beginRevealBeforeFreshFrame", new Class<?>[0]);
        assertTrue("cached static layer must become presentation-visible during HOME animation",
                visible(state));
        assertTrue("early reveal must request exactly one compositor fade",
                (Boolean) call(state, "consumeFadeReveal", new Class<?>[0]));
        assertFalse((Boolean) call(state, "consumeFadeReveal", new Class<?>[0]));

        call(state, "onFreshFrameReady", new Class<?>[]{long.class}, generation(state));
        assertTrue(visible(state));
        assertFalse("fresh frame must not start a second fade after cached early reveal",
                (Boolean) call(state, "consumeFadeReveal", new Class<?>[0]));
    }
}
