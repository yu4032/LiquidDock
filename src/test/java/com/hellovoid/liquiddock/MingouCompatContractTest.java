package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Opt-in vendor compatibility and popup chrome clipping regression contracts. */
public class MingouCompatContractTest {
    private static final Path JAVA = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path KOTLIN = Path.of("src/main/kotlin/com/hellovoid/liquiddock");

    @Test public void folderLoadRepairIsUserControlledAndNeverBuildDetected() throws Exception {
        String hook = Files.readString(JAVA.resolve("LauncherMingouCompatHook.java"));
        String schema = Files.readString(JAVA.resolve("config/ConfigSchema.java"));
        String entry = Files.readString(JAVA.resolve("ModuleMain.java"));
        String ui = Files.readString(KOTLIN.resolve("GlassSettingsPages.kt"));

        assertTrue(schema.contains("\"mingou_large_folder_compat\", false, false, false"));
        assertTrue(entry.contains("LauncherMingouCompatHook.install(classLoader)"));
        assertTrue(hook.contains("if (!enabled) return chain.proceed"));
        assertTrue(hook.contains("if (!enabled || dbSpanX != 2 || dbSpanY != 2) return result"));
        assertTrue(hook.contains("LauncherLargeFolderPickerHook.isMarked(context, info)"));
        assertTrue(hook.contains("LargeFolderFootprintPolicy.supportsType(type)"));
        assertTrue(hook.contains("preferences.registerOnSharedPreferenceChangeListener(listener)"));
        assertFalse(hook.contains("isMingouLauncher("));
        assertFalse(hook.contains("getPackageInfo("));
        assertFalse(hook.contains("getLongVersionCode("));
        assertTrue(ui.contains("ConfigSchema.Glass.MINGOU_LARGE_FOLDER_COMPAT"));
    }

    @Test public void popupContentDoesNotGetSecondAlphaScaleLayer() throws Exception {
        String dialog = Files.readString(KOTLIN.resolve("SettingsDialogMotion.kt"));
        String restart = Files.readString(KOTLIN.resolve("SettingsRestartScopesDialog.kt"));

        assertTrue(dialog.contains("WindowDialog("));
        assertTrue(dialog.contains("Column(content = content)"));
        assertFalse(dialog.contains("Modifier.graphicsLayer"));
        assertFalse(dialog.contains("updateTransition("));
        int shadowGutter = restart.indexOf(".graphicsLayer { scaleX = panelScale; scaleY = panelScale }");
        int padding = restart.indexOf(".padding(horizontal = 20.dp, vertical = 28.dp)");
        assertTrue(shadowGutter >= 0 && padding > shadowGutter);
    }
}
