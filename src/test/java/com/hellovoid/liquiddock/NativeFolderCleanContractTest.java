package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Source-level isolation checks, not substitutes for Launcher 4.50 device tests. */
public class NativeFolderCleanContractTest {
    private static final Path ROOT = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String code(String file) throws Exception {
        return Files.readString(ROOT.resolve(file));
    }

    @Test public void popupStateIsOwnedByTheSheetTree() throws Exception {
        String picker = code("LauncherLargeFolderPickerHook.java");
        assertFalse(picker.contains("WeakHashMap"));
        assertFalse(picker.contains("STATES"));
        assertTrue(picker.contains("ROW_ID = View.generateViewId()"));
        assertTrue(picker.contains("row.setTag(state)"));
        assertTrue(picker.contains("stateFor(owner)"));
        assertTrue(picker.contains("开启真实大文件夹"));
        assertTrue(picker.contains("findNativeToggle(predictionField, 0)"));
        assertFalse(picker.contains("new Switch(context)"));
        assertTrue(picker.contains("if (clicked == ok)"));
    }

    @Test public void renderSizingHasScopedWeakCacheWithoutHotPathRescan() throws Exception {
        String nativeHook = code("LauncherNativeFourFolderHook.java");
        assertTrue(nativeHook.contains("WeakHashMap<ViewGroup, WorkspaceIconMetrics>"));
        assertTrue(nativeHook.contains("WeakReference<Object> grid"));
        assertTrue(nativeHook.contains("metrics.matches(grid, cellW, cellH, baseIcon"));
        assertTrue(nativeHook.contains("orientation, density, uiMode, direction"));
        assertTrue(nativeHook.contains("refreshUnmeasuredMetricsAfterLayout(workspace)"));
        assertTrue(nativeHook.contains("isCustomFolder(View folder)"));
        assertTrue(nativeHook.contains("isCustomFolderInfo(View contextOwner, Object info)"));
        assertFalse(nativeHook.contains("tracePreviewChildren("));
        assertFalse(nativeHook.contains("traceMeasure("));
    }

    @Test public void gridOnlyExtendsSelectedVendorFolderAndKeepsWidgetsUntouched() throws Exception {
        String grid = code("HomeGridCellGeometryHook.java");
        assertTrue(grid.contains("LauncherNativeFourFolderHook.isCustomFolder(child)"));
        assertTrue(grid.contains("LauncherNativeFourFolderHook.isCustomFolderInfo("));
        assertFalse(grid.contains("|| LauncherNativeFourFolderHook.isFourGridInfo(info)"));
        String launcher = code("ModuleMain.java");
        assertTrue(launcher.contains("LauncherLargeFolderPickerHook.install(classLoader)"));
        assertTrue(launcher.contains("LauncherLargeFolderNativeSpanHook.install(classLoader)"));
        assertTrue(launcher.contains("LauncherNativeFourFolderHook.install(classLoader)"));
        assertFalse(launcher.contains("LauncherLargeFolderRuntimeAuditHook.install("));
        assertFalse(Files.exists(ROOT.resolve("LauncherLargeFolderRuntimeAuditHook.java")));
    }

    @Test public void titleUsesModelFlagsRatherThanStaleDragVisibility() throws Exception {
        String nativeHook = code("LauncherNativeFourFolderHook.java");
        assertTrue(nativeHook.contains("queueTitleRestore(folder)"));
        assertTrue(nativeHook.contains("needHideTitleByFlags"));
        assertFalse(nativeHook.contains("if (title.getVisibility() == View.GONE) return;"));
    }
}
