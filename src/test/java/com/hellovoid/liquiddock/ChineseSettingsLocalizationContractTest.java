package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class ChineseSettingsLocalizationContractTest {
    @Test
    public void gridAndWorkspaceHighlightLabelsHaveCurrentChineseCopy() throws Exception {
        String strings = Files.readString(Path.of(
                "src/main/res/values-zh-rCN/strings.xml"));
        String arrays = Files.readString(Path.of(
                "src/main/res/values-zh-rCN/arrays.xml"));

        assertTrue(strings.contains("name=\"grid_profile_title\">网格布局<"));
        assertTrue(strings.contains("name=\"enable_grid_8x4\">启用自定义布局<"));
        assertTrue(strings.contains("name=\"launcher_highlights_entry\">图标、文件夹与小组件高光层<"));
        assertTrue(strings.contains("控制工作区图标、文件夹与小组件玻璃使用的高光层"));
        assertTrue(arrays.contains("8×4 横屏 / 4×8 竖屏"));
        assertTrue(arrays.contains("10×6 横屏 / 6×10 竖屏"));
    }
}
