package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Folder-native blur is restored by OEM ownership, never an arbitrary global blur setter. */
public class FolderRecentsNativeBlurHandoffContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void nativeFolderBlurIsReassertedOnlyAfterSettledRecentsReturn() throws Exception {
        String coordinator = Files.readString(MAIN.resolve("OpenFolderGlassCoordinator.java"));
        String recents = Files.readString(MAIN.resolve("LauncherGlassRecentsHook.java"));

        assertTrue(coordinator.contains("if (!OWNER.completeRecentsReturn(state.serial)) return;"));
        assertTrue(coordinator.contains("OemFolderBlurStrengthHook.restoreAfterRecentsReturn();"));
        assertTrue(coordinator.indexOf("if (!OWNER.completeRecentsReturn(state.serial)) return;")
                < coordinator.indexOf("OemFolderBlurStrengthHook.restoreAfterRecentsReturn();"));
        assertTrue(coordinator.contains("!state.motion.isClosing()"));
        assertTrue(coordinator.contains("!OWNER.isRecentsReturnPending(state.serial)"));
        assertFalse("Do not restart folder blur at Recents-hide before wallpaper settles",
                recents.contains("OemFolderBlurStrengthHook.restoreAfterRecentsReturn()"));
    }

    @Test public void restorationUsesOriginalLauncherFolderBlurEntryAndExistingCap() throws Exception {
        String hook = Files.readString(MAIN.resolve("OemFolderBlurStrengthHook.java"));
        assertTrue(hook.contains("nativeFolderBlur = wrapper;"));
        assertTrue(hook.contains("method.invoke(null, args);"));
        assertTrue(hook.contains("Math.min(requested, OPEN_STRENGTH)"));
        assertTrue(hook.contains("!actualFolderOpening"));
        assertTrue(hook.contains("!GlassRuntimeState.isEnabled()"));
        assertFalse("Do not install a second Window/SurfaceControl blur owner",
                hook.contains("setBackgroundBlurRadius("));
    }
}
