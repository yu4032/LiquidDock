package com.hellovoid.liquiddock;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class SecurityCenterSceneSettingsPageContractTest {
    private static final Path ROOT = Path.of("src/main/kotlin/com/hellovoid/liquiddock/");

    @Test public void overviewOnlyHasNavigationCardsWithStandardGaps() throws Exception {
        String src = Files.readString(ROOT.resolve("SettingsUtilityPages.kt"));
        assertTrue(src.contains("open: (Page) -> Unit"));
        assertTrue(src.contains("onClick = { open(scene.page) }"));
        assertTrue(src.contains("verticalArrangement = Arrangement.spacedBy(10.dp)"));
        assertTrue(src.contains("modifier = Modifier.padding(horizontal = 14.dp)"));
        assertFalse(src.contains("groupedIntSettings("));
    }

    @Test public void leafKeepsSwitchSeparateFromSpacedBlurAndColorGroups() throws Exception {
        String leaf = Files.readString(ROOT.resolve("SecurityCenterSceneSettingsPage.kt"));
        assertTrue(leaf.contains("DenseSettingsList("));
        assertTrue(leaf.contains("BooleanSetting("));
        assertTrue(leaf.contains("IntSetting(prefs, scene.specs.first()"));
        assertTrue(leaf.contains("groupedIntSettings(scene.specs.drop(1)"));
        assertTrue(leaf.contains("canEdit && sceneEnabled"));
    }

    @Test public void sceneRoutesAndRestartScopeAreWired() throws Exception {
        String nav = Files.readString(ROOT.resolve("SettingsNavigationModel.kt"));
        String compose = Files.readString(ROOT.resolve("ComposeSettingsActivity.kt"));
        for (String title : new String[]{"SecurityCenterDock", "SecurityCenterAllApps",
                "SecurityCenterGameToolbox", "SecurityCenterVideoToolbox"}) {
            assertTrue(nav.contains(title + "(R.string.page_security_center_"));
            assertTrue(compose.contains("Page." + title + ","));
        }
        assertTrue(compose.contains("SecurityCenterSceneSettingsPage("));
        assertTrue(compose.contains("sidebarSceneSettings.first { it.page == target }"));
    }
}
