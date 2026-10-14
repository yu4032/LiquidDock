package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Scope and lifecycle contracts for the opt-in 4-tile native large folder. */
public class LauncherLargeFolderFourIconContractTest {
    private static final Path JAVA = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void fourTileModePreservesNativeFolderModelAndVariant() throws Exception {
        String hook = Files.readString(JAVA.resolve("LauncherLargeFolderFourIconHook.java"));
        assertTrue(hook.contains("com.miui.home.launcher.folder.FolderIcon2x2_4"));
        assertTrue(hook.contains("com.miui.home.launcher.folder.FolderIconPreviewContainer2X2_4"));
        assertTrue(hook.contains("FOUR_FOLDER.equals(owner.getClass().getName())"));
        assertTrue(hook.contains("FOUR_PREVIEW.equals(preview.getClass().getName())"));
        assertFalse(hook.contains("setSpanX"));
        assertFalse(hook.contains("setSpanY"));
        assertFalse(hook.contains("CellLayout"));
        assertFalse(hook.contains("ScreenCapture"));
        assertFalse(hook.contains("PixelCopy"));
    }

    @Test public void previewAndClickListenersShareSameFourIconLimit() throws Exception {
        String hook = Files.readString(JAVA.resolve("LauncherLargeFolderFourIconHook.java"));
        assertTrue(hook.contains("\"onFinishInflate\""));
        assertTrue(hook.contains("\"createOrRemoveView\""));
        assertTrue(hook.contains("\"addItemOnclickListener\""));
        assertTrue(hook.contains("\"preSetup2x2\""));
        assertTrue(hook.contains("\"setMItemsMaxCount\", FOUR"));
        assertTrue(hook.contains("\"setMLargeIconNum\", FOUR"));
        assertTrue(hook.contains("chain.proceed("));
        assertFalse(hook.contains("\"dispatchTouchEvent\""));
    }

    @Test public void optInIsIndependentOfCustomGridAndWiredEndToEnd() throws Exception {
        String schema = Files.readString(JAVA.resolve("config/ConfigSchema.java"));
        String config = Files.readString(JAVA.resolve("LiquidDockConfig.java"));
        String bootstrap = Files.readString(JAVA.resolve("ModuleMain.java"));
        String ui = Files.readString(Path.of("src/main/kotlin/com/hellovoid/liquiddock/GridSettingsPages.kt"));
        assertTrue(schema.contains("\"grid_large_folder_four_icons\", false, false, false"));
        assertTrue(schema.contains("Grid.WIDGET_HORIZONTAL_STRETCH, Grid.LARGE_FOLDER_FOUR_ICONS"));
        assertTrue(config.contains("largeFolderFourIcons = c.b("));
        assertTrue(bootstrap.contains("runtimeConfig.enabled && runtimeConfig.grid.largeFolderFourIcons"));
        assertTrue(ui.contains("ConfigSchema.Grid.LARGE_FOLDER_FOUR_ICONS"));
        assertTrue(ui.contains("masterEnabled,"));
    }
}
