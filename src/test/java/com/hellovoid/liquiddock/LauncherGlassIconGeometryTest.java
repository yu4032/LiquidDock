package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Test;

/** Pure fallback geometry for label-bearing ShortcutIcon hosts. */
public class LauncherGlassIconGeometryTest {
    private static Object fallback(
            int hostWidth, int hostHeight, int iconWidth, int iconHeight, int topOffset)
            throws Exception {
        Class<?> type = Class.forName("com.hellovoid.liquiddock.LauncherGlassIconGeometry");
        Method method = type.getDeclaredMethod("fallback",
                int.class, int.class, int.class, int.class, int.class);
        method.setAccessible(true);
        return method.invoke(null, hostWidth, hostHeight, iconWidth, iconHeight, topOffset);
    }

    private static float field(Object bounds, String name) throws Exception {
        Field field = bounds.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getFloat(bounds);
    }

    @Test
    public void padShortcutIconUsesCentered173SquareInsteadOf203By233LabelView() throws Exception {
        Object bounds = fallback(203, 233, 173, 173, 30);
        assertEquals(15f, field(bounds, "left"), 0.01f);
        assertEquals(30f, field(bounds, "top"), 0.01f);
        assertEquals(188f, field(bounds, "right"), 0.01f);
        assertEquals(203f, field(bounds, "bottom"), 0.01f);
        assertTrue(field(bounds, "bottom") < 233f);
    }

    @Test
    public void alternateDensityIconStillExcludesLabelArea() throws Exception {
        Object bounds = fallback(196, 230, 163, 163, 30);
        assertEquals(16.5f, field(bounds, "left"), 0.01f);
        assertEquals(179.5f, field(bounds, "right"), 0.01f);
        assertEquals(193f, field(bounds, "bottom"), 0.01f);
        assertTrue(field(bounds, "bottom") < 230f);
    }

    @Test
    public void invalidOversizedDrawableIsClampedInsideHost() throws Exception {
        Object bounds = fallback(100, 120, 200, 200, 50);
        assertTrue(field(bounds, "left") >= 0f);
        assertTrue(field(bounds, "top") >= 0f);
        assertTrue(field(bounds, "right") <= 100f);
        assertTrue(field(bounds, "bottom") <= 120f);
    }
}
