package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class LauncherHighlightSettingsGroupingContractTest {
    @Test
    public void compactAndLargeSurfaceHighlightsUseSeparateCards() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        int start = source.indexOf("private fun LauncherHighlightsPage(");
        int end = source.indexOf("\n@Composable\nprivate fun StrokePage", start);
        String page = source.substring(start, end);

        assertTrue(page.contains("SmallTitle(\"图标、小文件夹与 Dock 图标\")"));
        assertTrue(page.contains("SmallTitle(\"小组件与大文件夹\")"));
        assertTrue(count(page, "SettingsCard {") >= 2);
    }

    private static int count(String value, String needle) {
        int count = 0;
        for (int index = 0; (index = value.indexOf(needle, index)) >= 0; index += needle.length()) {
            count++;
        }
        return count;
    }
}
