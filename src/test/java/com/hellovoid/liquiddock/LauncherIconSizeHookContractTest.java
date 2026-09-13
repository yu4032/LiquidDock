package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Architecture contract for vendor-owned icon sizing. */
public class LauncherIconSizeHookContractTest {
    private static final Path MAIN = Paths.get("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void hookUsesItemIconBindBoundaryForWorkspaceDockAndSmallFolder() throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.hellovoid.liquiddock.LauncherIconSizeHook");
        } catch (ClassNotFoundException missing) {
            assertTrue("LauncherIconSizeHook must exist", false);
            return;
        }
        Method install = type.getDeclaredMethod("install", ClassLoader.class, boolean.class, int.class);
        assertNotNull(install);

        String source = Files.readString(MAIN.resolve("LauncherIconSizeHook.java"));
        assertTrue(source.contains("com.miui.home.launcher.ItemIcon"));
        assertTrue(source.contains("setIconImageView"));
        assertTrue(source.contains("ShortcutIcon"));
        assertTrue(source.contains("FolderIcon1x1"));
        assertTrue(source.contains("mIconImageView"));
        assertFalse(source.contains("setScaleX("));
        assertFalse(source.contains("setScaleY("));
    }

    @Test
    public void shortcutSizingIsLimitedToWorkspaceAndDockAfterAttachment() throws Exception {
        String source = Files.readString(MAIN.resolve("LauncherIconSizeHook.java"));
        assertTrue(source.contains("LauncherGlassHierarchy.classify"));
        assertTrue(source.contains("LauncherGlassHierarchy.Domain.WORKSPACE"));
        assertTrue(source.contains("LauncherGlassHierarchy.Domain.DOCK"));
        assertTrue(source.contains("View.OnAttachStateChangeListener"));
    }
}
