package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Test;

public class LauncherGlassRenderDomainContractTest {
    @Test public void dragInvalidationOnlyDirtiesDragDomain() throws Exception {
        LauncherGlassFramePolicy policy = new LauncherGlassFramePolicy();
        Method requestDrag = LauncherGlassFramePolicy.class.getDeclaredMethod("requestDrag");
        requestDrag.setAccessible(true);
        assertTrue((Boolean) requestDrag.invoke(policy));
        LauncherGlassFramePolicy.Work work = policy.consume();
        assertTrue(readBoolean(work, "dragDirty"));
        assertFalse(readBoolean(work, "staticDirty"));
    }

    @Test public void staticInvalidationOnlyDirtiesStaticDomain() throws Exception {
        LauncherGlassFramePolicy policy = new LauncherGlassFramePolicy();
        Method requestStatic = LauncherGlassFramePolicy.class.getDeclaredMethod("requestStatic");
        requestStatic.setAccessible(true);
        assertTrue((Boolean) requestStatic.invoke(policy));
        LauncherGlassFramePolicy.Work work = policy.consume();
        assertTrue(readBoolean(work, "staticDirty"));
        assertFalse(readBoolean(work, "dragDirty"));
    }

    private static boolean readBoolean(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(object);
    }
}
