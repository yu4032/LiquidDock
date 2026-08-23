package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.Test;

public class LauncherGlassSceneOwnershipRegressionTest {
    private static Object tracker() throws Exception {
        Class<?> type = Class.forName(
                "com.hellovoid.liquiddock.LauncherGlassRootTransformTracker");
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static boolean update(Object tracker, float[] points) throws Exception {
        Method method = tracker.getClass().getDeclaredMethod("update", float[].class);
        method.setAccessible(true);
        return (Boolean) method.invoke(tracker, new Object[]{points});
    }

    @Test public void rootTransformTrackerDetectsParentOnlyMotion() throws Exception {
        Object tracker = tracker();
        float[] initial = {0f, 0f, 100f, 0f, 0f, 100f, 100f, 100f};
        assertTrue(update(tracker, initial));
        assertFalse(update(tracker, initial.clone()));
        assertTrue(update(tracker, new float[]{12f, 4f, 112f, 4f, 12f, 104f, 112f, 104f}));
    }
}
