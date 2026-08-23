package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Drag motion must not redraw the unchanged full-screen Workspace glass layer. */
public class LauncherGlassRenderDomainContractTest {
    @Test public void dragInvalidationOnlyDirtiesDragDomain() throws Exception {
        LauncherGlassFramePolicy policy = new LauncherGlassFramePolicy();
        Method requestDrag = LauncherGlassFramePolicy.class.getDeclaredMethod("requestDrag");
        requestDrag.setAccessible(true);
        assertTrue((Boolean) requestDrag.invoke(policy));

        LauncherGlassFramePolicy.Work work = policy.consume();
        assertTrue(readBoolean(work, "dragDirty"));
        assertFalse(readBoolean(work, "staticDirty"));
        assertFalse(work.refreshProducer);
        assertFalse(work.rebuildBackdrop);
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

    @Test public void freshBackdropDirtiesBothDomainsBecauseTheirPixelsChanged() throws Exception {
        LauncherGlassFramePolicy policy = new LauncherGlassFramePolicy();
        assertTrue(policy.requestBackdropRefresh());
        LauncherGlassFramePolicy.Work work = policy.consume();
        assertTrue(readBoolean(work, "staticDirty"));
        assertTrue(readBoolean(work, "dragDirty"));
        assertTrue(work.refreshProducer);
        assertTrue(work.rebuildBackdrop);
    }

    @Test public void sessionConditionallyRendersStaticAndDragOutputs() throws Exception {
        String session = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java"));
        assertTrue(session.contains("if (work.staticDirty)"));
        assertTrue(session.contains("renderStaticScene"));
        assertTrue(session.contains("if (work.dragDirty)"));
        assertTrue(session.contains("renderDragOutputs"));
    }

    private static boolean readBoolean(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(object);
    }
}
