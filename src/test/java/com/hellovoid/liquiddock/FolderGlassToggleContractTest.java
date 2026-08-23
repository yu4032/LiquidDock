package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Contracts the independent GUI/runtime switch for Launcher folder glass. */
public class FolderGlassToggleContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test public void folderGlassHasIndependentDefaultOnConfigAndRuntimeGate() throws Exception {
 String s=read("src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"),r=read("src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java"),h=read("src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java");
 assertTrue(s.contains("SMALL_FOLDER_GLASS = bool(")&&s.contains("LARGE_FOLDER_GLASS = bool(")); assertTrue(r.contains("GlassComponentStyle smallFolderStyle")&&r.contains("GlassComponentStyle largeFolderStyle")&&r.contains(": legacyFolderEnabled")); assertTrue(h.contains("smallFolder ? glassConfig.smallFolderStyle : glassConfig.largeFolderStyle"));
    }

    @Test public void liquidSettingsExposeFolderGlassSwitchUnderMasterGlassSwitch() throws Exception {
 String u=read("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"); assertTrue(u.contains("ConfigSchema.Glass.SMALL_FOLDER_GLASS")&&u.contains("ConfigSchema.Glass.LARGE_FOLDER_GLASS")&&u.contains("smallFolderGlass")&&u.contains("largeFolderGlass"));
    }

}
