package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigKey;
import com.hellovoid.liquiddock.config.ConfigSchema;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Expanded FolderCling background is independent of desktop folder icon glass. */
public class OpenFolderGlassEnableConfigTest {
    @Test public void legacyInstallationPreservesExpandedBackgroundByDefault() {
        LiquidDockConfig config = LiquidDockConfig.from(new ConfigReader(Map.of()));
        assertTrue(ConfigSchema.Glass.OPEN_FOLDER_GLASS.runtimeFallback());
        assertTrue(config.glass.openFolderEnabled);
        assertTrue(config.glass.smallFolderStyle.enabled);
        assertTrue(config.glass.largeFolderStyle.enabled);
    }

    @Test public void disablingExpandedBackgroundDoesNotDisableDesktopFolderGlass() {
        Map<String, Object> values = new HashMap<>();
        values.put(ConfigSchema.Glass.OPEN_FOLDER_GLASS.name(), false);
        values.put(ConfigSchema.Glass.SMALL_FOLDER_GLASS.name(), true);
        values.put(ConfigSchema.Glass.LARGE_FOLDER_GLASS.name(), true);
        LiquidDockConfig config = LiquidDockConfig.from(new ConfigReader(values));
        assertFalse(config.glass.openFolderEnabled);
        assertTrue(config.glass.smallFolderStyle.enabled);
        assertTrue(config.glass.largeFolderStyle.enabled);
        assertTrue(config.glass.folderEnabled);
    }

    @Test public void expandedBackgroundCanBeEnabledIndependentlyOfIconGlass() {
        Map<String, Object> values = new HashMap<>();
        values.put(ConfigSchema.Glass.OPEN_FOLDER_GLASS.name(), true);
        values.put(ConfigSchema.Glass.SMALL_FOLDER_GLASS.name(), false);
        values.put(ConfigSchema.Glass.LARGE_FOLDER_GLASS.name(), false);
        LiquidDockConfig config = LiquidDockConfig.from(new ConfigReader(values));
        assertTrue(config.glass.openFolderEnabled);
        assertFalse(config.glass.smallFolderStyle.enabled);
        assertFalse(config.glass.largeFolderStyle.enabled);
        assertFalse(config.glass.folderEnabled);
    }

    @Test public void persistedSwitchIsInSharedExportAndPresetSchema() {
        boolean registered = false;
        for (ConfigKey<?> key : ConfigSchema.all()) {
            if (ConfigSchema.Glass.OPEN_FOLDER_GLASS.name().equals(key.name())) {
                registered = true;
                break;
            }
        }
        assertTrue(registered);
    }
}
