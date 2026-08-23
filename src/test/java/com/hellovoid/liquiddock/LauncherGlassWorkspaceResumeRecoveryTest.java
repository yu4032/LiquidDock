package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.Test;

public class LauncherGlassWorkspaceResumeRecoveryTest {
    private static Object tracker() throws Exception {
        Class<?> type = Class.forName(
                "com.hellovoid.liquiddock.LauncherGlassEffectiveVisibilityTracker");
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static boolean update(Object tracker, float alpha) throws Exception {
        Method method = tracker.getClass().getDeclaredMethod("update", float.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(tracker, alpha);
    }

    @Test public void ancestorHideAndRestoreBothInvalidateGeometry() throws Exception {
        Object tracker = tracker();
        assertTrue(update(tracker, 1f));
        assertFalse(update(tracker, 1f));
        assertTrue(update(tracker, 0f));
        assertFalse(update(tracker, 0f));
        assertTrue(update(tracker, 1f));
    }

    @Test public void nonFiniteEffectiveAlphaTransitionsDeterministically() throws Exception {
        Object tracker = tracker();
        assertTrue(update(tracker, Float.NaN));
        assertFalse(update(tracker, Float.NaN));
        assertTrue(update(tracker, 1f));
    }
}
