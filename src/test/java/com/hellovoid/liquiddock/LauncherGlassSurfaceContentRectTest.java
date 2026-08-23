package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Test;

/** Pure contract for mapping a ViewRoot surface buffer into Decor/root content UV space. */
public class LauncherGlassSurfaceContentRectTest {
    private static Object resolve(int width, int height, int left, int top, int right, int bottom)
            throws Exception {
        Class<?> type = Class.forName("com.hellovoid.liquiddock.LauncherGlassSurfaceContentRect");
        Method method = type.getDeclaredMethod("resolve",
                int.class, int.class, int.class, int.class, int.class, int.class);
        method.setAccessible(true);
        return method.invoke(null, width, height, left, top, right, bottom);
    }

    private static float field(Object rect, String name) throws Exception {
        assertNotNull(rect);
        Field field = rect.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getFloat(rect);
    }

    @Test
    public void zeroInsetsUseTheWholeSurfaceBuffer() throws Exception {
        Object rect = resolve(3008, 1880, 0, 0, 0, 0);
        assertEquals(0f, field(rect, "left"), 0.00001f);
        assertEquals(0f, field(rect, "bottom"), 0.00001f);
        assertEquals(1f, field(rect, "width"), 0.00001f);
        assertEquals(1f, field(rect, "height"), 0.00001f);
    }

    @Test
    public void asymmetricSurfaceInsetsMapContentInsteadOfWholeBuffer() throws Exception {
        Object rect = resolve(3008, 1880, 8, 12, 4, 6);
        assertEquals(8f / 3008f, field(rect, "left"), 0.00001f);
        assertEquals(6f / 1880f, field(rect, "bottom"), 0.00001f);
        assertEquals((3008f - 8f - 4f) / 3008f, field(rect, "width"), 0.00001f);
        assertEquals((1880f - 12f - 6f) / 1880f, field(rect, "height"), 0.00001f);
    }

    @Test
    public void invalidInsetsFallBackToWholeBufferRatherThanEmptyContent() throws Exception {
        Object rect = resolve(100, 100, 80, 0, 40, 0);
        assertEquals(0f, field(rect, "left"), 0.00001f);
        assertEquals(0f, field(rect, "bottom"), 0.00001f);
        assertEquals(1f, field(rect, "width"), 0.00001f);
        assertEquals(1f, field(rect, "height"), 0.00001f);
    }
}
