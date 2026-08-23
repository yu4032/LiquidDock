package com.hellovoid.liquiddock;

import static org.junit.Assert.assertArrayEquals;

import java.lang.reflect.Method;

import org.junit.Test;

/** Symmetric component size offset policy. */
public class LauncherGlassBoundsPolicyTest {
    private static float[] apply(float left, float top, float right, float bottom, float offset)
            throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.hellovoid.liquiddock.LauncherGlassBoundsPolicy");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("missing LauncherGlassBoundsPolicy", missing);
        }
        Method method = type.getDeclaredMethod("apply", float.class, float.class,
                float.class, float.class, float.class);
        method.setAccessible(true);
        return (float[]) method.invoke(null, left, top, right, bottom, offset);
    }

    @Test public void positiveOffsetExpandsEveryEdge() throws Exception {
        assertArrayEquals(new float[]{-12f, -12f, 112f, 212f},
                apply(0f, 0f, 100f, 200f, 12f), 0.0001f);
    }

    @Test public void negativeOffsetInsetsEveryEdge() throws Exception {
        assertArrayEquals(new float[]{20f, 20f, 80f, 180f},
                apply(0f, 0f, 100f, 200f, -20f), 0.0001f);
    }

    @Test public void oversizedInsetStillProducesPositiveBounds() throws Exception {
        float[] bounds = apply(0f, 0f, 20f, 20f, -50f);
        org.junit.Assert.assertTrue(bounds[2] > bounds[0]);
        org.junit.Assert.assertTrue(bounds[3] > bounds[1]);
    }
}
