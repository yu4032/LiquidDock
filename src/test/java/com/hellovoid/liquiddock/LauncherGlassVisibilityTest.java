package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Ancestor visibility must be accumulated instead of checking only the leaf material View. */
public class LauncherGlassVisibilityTest {
    private static float aggregate(boolean[] visibility, float[] alpha) throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.hellovoid.liquiddock.LauncherGlassVisibility");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("missing LauncherGlassVisibility", missing);
        }
        Method method = type.getDeclaredMethod("aggregate", boolean[].class, float[].class);
        method.setAccessible(true);
        return (Float) method.invoke(null, visibility, alpha);
    }

    @Test public void invisibleAncestorCollapsesEffectiveAlpha() throws Exception {
        assertEquals(0f, aggregate(new boolean[]{true, false, true},
                new float[]{1f, 1f, 1f}), 0.0001f);
    }

    @Test public void visibleAncestorAlphasMultiply() throws Exception {
        assertEquals(0.2f, aggregate(new boolean[]{true, true, true},
                new float[]{0.5f, 0.8f, 0.5f}), 0.0001f);
    }

    @Test public void nonFiniteOrZeroAlphaIsNotVisible() throws Exception {
        assertEquals(0f, aggregate(new boolean[]{true}, new float[]{Float.NaN}), 0.0001f);
        assertEquals(0f, aggregate(new boolean[]{true}, new float[]{0f}), 0.0001f);
    }

    @Test public void staticNodeUsesAncestorAwareVisibility() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java"));
        assertTrue(source.contains("LauncherGlassVisibility.isVisible("));
        assertFalse(source.contains("|| !material.isShown()"));
    }
}
