package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.liquiddock.config.PresetManager;
import org.junit.Test;

/** Typed behavior tests for the opt-in vendor folder load repair. */
public class MingouCompatContractTest {
    @Test public void compatibilityIsDisabledInEveryDefaultPath() {
        assertFalse(ConfigSchema.Glass.MINGOU_LARGE_FOLDER_COMPAT.uiDefault());
        assertFalse(ConfigSchema.Glass.MINGOU_LARGE_FOLDER_COMPAT.runtimeFallback());
        assertFalse(ConfigSchema.Glass.MINGOU_LARGE_FOLDER_COMPAT.exportDefault());
        assertTrue(ConfigSchema.all().contains(ConfigSchema.Glass.MINGOU_LARGE_FOLDER_COMPAT));
        assertEquals(Boolean.FALSE, PresetManager.defaultValues().get(
                ConfigSchema.Glass.MINGOU_LARGE_FOLDER_COMPAT.name()));
    }

    @Test public void neverChangesAnUnmarkedUncommittedOrAlreadyCorrectFolder() {
        assertFalse(LargeFolderFootprintPolicy.shouldRestoreLoadedTwoByTwo(
                false, 21, true, 2, 2, 1, 1));
        assertFalse(LargeFolderFootprintPolicy.shouldRestoreLoadedTwoByTwo(
                true, 21, false, 2, 2, 1, 1));
        assertFalse(LargeFolderFootprintPolicy.shouldRestoreLoadedTwoByTwo(
                true, 21, true, 1, 1, 1, 1));
        assertFalse(LargeFolderFootprintPolicy.shouldRestoreLoadedTwoByTwo(
                true, 21, true, 2, 1, 1, 1));
        assertFalse(LargeFolderFootprintPolicy.shouldRestoreLoadedTwoByTwo(
                true, 20, true, 2, 2, 1, 1));
        assertFalse(LargeFolderFootprintPolicy.shouldRestoreLoadedTwoByTwo(
                true, 21, true, 2, 2, 2, 2));
    }

    @Test public void onlyRestoresMarkedNativeLargeFolderTypesStoredAsTwoByTwo() {
        assertTrue(LargeFolderFootprintPolicy.shouldRestoreLoadedTwoByTwo(
                true, 21, true, 2, 2, 1, 1));
        assertTrue(LargeFolderFootprintPolicy.shouldRestoreLoadedTwoByTwo(
                true, 22, true, 2, 2, 2, 1));
    }
}
