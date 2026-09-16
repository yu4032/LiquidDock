package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contract for the two HyperOS Pad Recents action capsules. */
public class LauncherRecentsCapsuleGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(String name) throws Exception {
        Path path = MAIN.resolve(name);
        return Files.exists(path) ? Files.readString(path) : "";
    }

    @Test public void recentsInstallsDedicatedStableCapsuleHook() throws Exception {
        String recents = read("LauncherGlassRecentsHook.java");
        String capsule = read("LauncherRecentsCapsuleGlassHook.java");
        assertTrue(recents.contains("LauncherRecentsCapsuleGlassHook.install(classLoader)"));
        assertTrue(capsule.contains("com.miui.home.recents.views.RecentsDecorations"));
        assertTrue(capsule.contains("\"findAndSetupViews\""));
    }

    @Test public void targetsOnlyDecompiledStableResourceOwners() throws Exception {
        String capsule = read("LauncherRecentsCapsuleGlassHook.java");
        assertTrue(capsule.contains("recent_clear_all_task_container_for_pad"));
        assertTrue(capsule.contains("world_container"));
        assertFalse(capsule.contains("mClearAllTaskContainerForPad"));
        assertFalse(capsule.contains("mWorldContainer"));
        assertFalse(capsule.contains("getDeclaredField"));
        assertFalse(capsule.contains("0x7f"));
    }

    @Test public void keepsVendorSilhouetteAndUsesNativeBackdropBlur() throws Exception {
        String capsule = read("LauncherRecentsCapsuleGlassHook.java");
        assertTrue(capsule.contains("MiBlurBridge.applyPassWindowBlur"));
        assertTrue(capsule.contains("MiBlurBridge.clearPassWindowBlur"));
        assertTrue(capsule.contains("addOnAttachStateChangeListener"));
        assertFalse(capsule.contains("setBackground("));
        assertFalse(capsule.contains("setOnClickListener"));
        assertFalse(capsule.contains("postDelayed"));
    }
}
