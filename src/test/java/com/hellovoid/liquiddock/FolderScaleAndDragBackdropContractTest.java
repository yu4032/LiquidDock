package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contracts for HyperOS 4.50 small-folder scale math and drag glass source authority. */
public class FolderScaleAndDragBackdropContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void folderScaleDownMathRunsInsideScaledIconDomain() throws Exception {
        String hook = Files.readString(MAIN.resolve("Launcher450IconSizeHook.java"));

        assertTrue(hook.contains("BASE_PROGRESS_SHORTCUT_ICON"));
        assertTrue(hook.contains("installFolderScaleTransactions"));
        assertTrue(hook.contains("\"scaleDownToFolder\""));
        assertTrue(hook.contains("withMeasureDomain"));
        assertTrue(hook.contains("MeasureDomain.WORKSPACE"));
    }

    @Test public void dragGlassUsesIndependentUpperWorkspaceBackdropAuthority() throws Exception {
        String overlay = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        String source = Files.readString(MAIN.resolve("LauncherDragSourceOverlay.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        assertTrue(source.contains("WindowManager.LayoutParams.TYPE_APPLICATION_PANEL"));
        assertTrue(source.contains("FLAG_NOT_TOUCHABLE"));
        assertTrue(source.contains("LiquidDockDragBackdropSource"));
        assertTrue(overlay.contains("LauncherDragSourceOverlay.attach"));
        assertTrue(overlay.contains("new LauncherGlassSession(sourceOverlay, glassConfig)"));
        assertTrue(overlay.contains("LauncherGlassSinkView.attachToExternalMaterial"));
        assertFalse(overlay.contains("LauncherGlassSinkView.attachToMaterial(\n                    carrier"));
        assertTrue(session.contains("freezeAfterNextFreshFrame"));
        assertTrue(session.contains("setUpdatesEnabled(false, \"launcher-drag-frozen\")"));
    }
}
