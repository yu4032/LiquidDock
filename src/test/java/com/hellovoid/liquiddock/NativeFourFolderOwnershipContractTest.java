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
        assertFalse("Folder geometry must not be written from the widget owner",
                grid.contains("LauncherNativeFourFolderHook"));
        assertFalse("No duplicate CellLayout.measureChild hook for folders",
                grid.contains("\"measureChild\""));
    }

    @Test public void nativeFolderHasOneGridPolicyAndFailClosedInstallation() throws Exception {
        String nativeHook = source("LauncherNativeFourFolderHook.java");
        assertTrue(nativeHook.contains("NativeFourFolderFramePolicy.resolve("));
        assertTrue(nativeHook.contains("prepareFolderLayoutParams("));
        assertTrue(nativeHook.contains("preflightGeometryAbi("));
        assertTrue(nativeHook.contains("capabilities.containsAll(EnumSet.allOf(Capability.class))"));
        assertTrue(nativeHook.contains("if (!installed || !nativeFolderClass(folder)"));
        assertFalse("Partial hook installation must never signal readiness",
                nativeHook.contains("installed = installedHooks > 0"));
    }
}
