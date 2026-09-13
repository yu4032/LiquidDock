package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Device-targeted contract for HyperOS Launcher 4.50 icon sizing. */
public class Launcher450IconSizeContractTest {
    @Test
    public void policyClampsToSupportedPercentRange() throws Exception {
        Class<?> policy;
        try {
            policy = Class.forName("com.hellovoid.liquiddock.Launcher450IconSizePolicy");
        } catch (ClassNotFoundException missing) {
            fail("Launcher450IconSizePolicy must exist");
            return;
        }
        Method scale = policy.getDeclaredMethod("scale", boolean.class, int.class);
        scale.setAccessible(true);
        assertEquals(1.0f, (Float) scale.invoke(null, false, 120), 0.0001f);
        assertEquals(0.8f, (Float) scale.invoke(null, true, 60), 0.0001f);
        assertEquals(1.0f, (Float) scale.invoke(null, true, 100), 0.0001f);
        assertEquals(1.2f, (Float) scale.invoke(null, true, 150), 0.0001f);
    }

    @Test
    public void runtimeUsesLauncher450MeasureAuthoritiesOnly() throws Exception {
        Path hookPath = Path.of("src/main/java/com/hellovoid/liquiddock/Launcher450IconSizeHook.java");
        assertTrue("Launcher 4.50 hook must exist", Files.exists(hookPath));
        String source = Files.readString(hookPath);

        assertTrue(source.contains("com.miui.home.launcher.ShortcutIcon"));
        assertTrue(source.contains("com.miui.home.launcher.folder.FolderIcon1x1"));
        assertTrue(source.contains("com.miui.home.launcher.grid.GridConfig"));
        assertTrue(source.contains("onMeasure"));
        assertTrue(source.contains("getIconSize"));
        assertTrue(source.contains("getDockIconWidth"));
        assertTrue(source.contains("ThreadLocal"));
        assertTrue(source.contains("finally"));

        assertFalse("4.50 implementation must not use the failed drawable-bind experiment",
                source.contains("setIconImageView"));
        assertFalse("4.50 implementation must not visually scale Views",
                source.contains("setScaleX") || source.contains("setScaleY"));
        assertFalse("4.50 implementation must not hook OS4/native Flutter paths",
                source.contains("libapp_launcher.so") || source.contains("Flutter"));
    }
}