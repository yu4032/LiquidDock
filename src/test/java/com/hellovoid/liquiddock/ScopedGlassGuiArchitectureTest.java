package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class ScopedGlassGuiArchitectureTest {
    private static String file(String name) throws Exception {
        return Files.readString(Path.of("src/main/kotlin/com/hellovoid/liquiddock/" + name));
    }

    @Test public void childPagesRemainIndependentAndAllParamScreensAreRouted() throws Exception {
        String shell = file("ComposeSettingsActivity.kt");
        String gboard = file("GboardSettingsPages.kt");
        String dialog = file("DialogGlassSettingsPage.kt");
        String search = file("SearchboxSettingsActivity.kt");
        String screen = file("ScopedGlassSettingsPage.kt");
        assertTrue(shell.contains("Page.GboardAll -> ScopedGlassSettingsPage("));
        assertTrue(shell.contains("Page.DialogAll -> ScopedGlassSettingsPage("));
        assertTrue(gboard.contains("summary = \"调整 Gboard 专属的完整 Prismal 光学参数"));
        assertTrue(gboard.contains("summary = \"调整系统搜索专属的完整 Prismal 光学参数"));
        assertTrue(dialog.contains("summary = \"调整桌面对话弹窗专属 Prismal 光学参数"));
        assertTrue(search.contains("ScopedGlassOptics.SEARCHBOX"));
        assertTrue(screen.contains("ScopedGlassOptics.key(scope, d.config)"));
        assertTrue(screen.contains("恢复全部参数继承"));
        assertFalse(screen.contains("prefs.edit().putInt(d.config.name()"));
    }
}
