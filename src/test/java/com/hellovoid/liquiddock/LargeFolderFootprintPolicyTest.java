package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class LargeFolderFootprintPolicyTest {
    @Test public void desktopFolderSelectionDoesNotCustomizeWorkstationAllAppsCopies() {
        assertTrue(LargeFolderFootprintPolicy.customizeDesktopFolder(true, false));
        assertFalse(LargeFolderFootprintPolicy.customizeDesktopFolder(true, true));
        assertFalse(LargeFolderFootprintPolicy.customizeDesktopFolder(false, false));
    }

    @Test public void allAppsOrdinaryFoldersAlwaysUseOneCellWithoutASelectionMarker() {
        // Loader-owned All Apps copies use the vendor container identity.
        assertTrue(LargeFolderFootprintPolicy.normalizeAllAppsCopy(false, -104L, 2, 2, 2));
        assertTrue(LargeFolderFootprintPolicy.normalizeAllAppsCopy(false, -104L, 2, 1, 2));
        assertTrue(LargeFolderFootprintPolicy.normalizeAllAppsCopy(false, -104L, 2, 2, 1));
        // A confirmed All Apps view is sufficient when a cloned model retains another container.
        assertTrue(LargeFolderFootprintPolicy.normalizeAllAppsCopy(true, -100L, 2, 2, 2));
        assertFalse(LargeFolderFootprintPolicy.normalizeAllAppsCopy(false, -100L, 2, 2, 2));
        assertFalse(LargeFolderFootprintPolicy.normalizeAllAppsCopy(false, -101L, 2, 2, 2));
        // Native 2x2 styles and already-correct ordinary folders are untouched.
        assertFalse(LargeFolderFootprintPolicy.normalizeAllAppsCopy(true, -104L, 21, 2, 2));
        assertFalse(LargeFolderFootprintPolicy.normalizeAllAppsCopy(true, -104L, 22, 2, 2));
        assertFalse(LargeFolderFootprintPolicy.normalizeAllAppsCopy(true, -104L, 2, 1, 1));
    }
    @Test public void fourAndNineShareMarkedRealFootprintOnly() {
        assertTrue(LargeFolderFootprintPolicy.supportsType(21));
        assertTrue(LargeFolderFootprintPolicy.supportsType(22));
        assertFalse(LargeFolderFootprintPolicy.supportsType(2));
        assertTrue(LargeFolderFootprintPolicy.hasRealTwoByTwoFootprint(21, 2, 2));
        assertTrue(LargeFolderFootprintPolicy.hasRealTwoByTwoFootprint(22, 2, 2));
        assertFalse(LargeFolderFootprintPolicy.hasRealTwoByTwoFootprint(21, 1, 1));
        assertFalse(LargeFolderFootprintPolicy.hasRealTwoByTwoFootprint(22, 1, 1));
        assertFalse(LargeFolderFootprintPolicy.hasRealTwoByTwoFootprint(2, 2, 2));
    }
}
