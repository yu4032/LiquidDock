package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/** Settings UI contract for the shared Launcher icon-size control. */
public class LauncherIconSizeGuiContractTest {
    @Test
    public void gridPageExposesOneSharedWorkspaceDockAndSmallFolderControl() throws Exception {
        Path ui = Paths.get("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");
        String source = Files.readString(ui);
        assertTrue(source.contains("ConfigSchema.Grid.ICON_SIZE_ENABLED"));
        assertTrue(source.contains("ConfigSchema.Grid.ICON_SIZE_PERCENT"));
        assertTrue(source.contains("自定义图标大小"));
        assertTrue(source.contains("工作区、Dock 与小文件夹"));
        assertTrue(source.contains("100% 为系统默认"));
    }
}
