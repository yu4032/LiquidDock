package com.hellovoid.liquiddock;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

/** Structural boundaries: native folders and widgets must not share final geometry writers. */
public class NativeFourFolderOwnershipContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock", name));
    }

    @Test public void gridGeometryReconcilesWidgetsButNeverNativeFolders() throws Exception {
        String grid = source("HomeGridCellGeometryHook.java");
        assertTrue(grid.contains("enforceWidgetGridFrames(layout)"));
        assertTrue(grid.contains("if (info == null || !isWidget(info)) continue;"));
        assertTrue(grid.contains("if (!isWidget(info)) return;"));
        assertTrue("Existing four-arg setup forwards custom folder writes to its owner",
                grid.contains("LauncherNativeFourFolderHook.prepareFolderLayoutParams("));
        assertFalse("HomeGrid must not independently re-measure native folders",
                grid.contains("\"measureChild\""));
        assertFalse("HomeGrid must not own the edit-aware native folder setup",
                grid.contains("boolean.class, paramsType"));
    }

    @Test public void nativeFolderHasOneGridPolicyAndIndependentPickerInstallation() throws Exception {
        String nativeHook = source("LauncherNativeFourFolderHook.java");
        assertTrue(nativeHook.contains("NativeFourFolderFramePolicy.resolve("));
        assertTrue(nativeHook.contains("prepareFolderLayoutParams("));
        assertTrue("First OEM measurement must still expand restored folder views",
                nativeHook.contains("HookUtil.hookMethod(cell, \"measureChild\""));
        assertTrue("Initial sizing must not require a parent already laid out",
                nativeHook.contains("NativeFourFolderFramePolicy.forInitialMeasure("));
        assertFalse("Avoid a second four-argument setupLayoutParam hook",
                nativeHook.contains("itemInfoClass, paramsClass),"));
        assertFalse("No permanent retry suppression state",
                nativeHook.contains("installAttempted"));
        String module = source("ModuleMain.java");
        assertFalse("Missing optional native geometry must never hide both folder toggles",
                module.contains("if (LauncherNativeFourFolderHook.install(classLoader)) {"));
        assertTrue(module.contains("LauncherLargeFolderPickerHook.install(classLoader);"));
        assertTrue(module.contains("LauncherLargeFolderNativeSpanHook.install(classLoader);"));
        String picker = source("LauncherLargeFolderPickerHook.java");
        assertTrue("Custom 2x2 toggle remains present", picker.contains("PRIMARY_TOGGLE_ID"));
        assertTrue("Title toggle remains present", picker.contains("TITLE_TOGGLE_ID"));
        assertTrue("Independent working OEM hooks must remain active",
                nativeHook.contains("installed = installedHooks > 0"));
        assertFalse("Do not require every optional vendor method",
                nativeHook.contains("capabilities.containsAll("));
        assertFalse("Do not reject Launcher builds during strict ABI preflight",
                nativeHook.contains("preflightGeometryAbi("));
    }
}
