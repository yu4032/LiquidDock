package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class LargeFolderFootprintPolicyTest {
    @Test public void desktopFolderSelectionDoesNotCustomizeWorkstationAllAppsCopies() {
        assertTrue(LargeFolderFootprintPolicy.customizeDesktopFolder(true, false));
        assertFalse(LargeFolderFootprintPolicy.customizeDesktopFolder(true, true));
        assertFalse(LargeFolderFootprintPolicy.customizeDesktopFolder(false, false));
    }

    @Test public void onlyTheAllAppsOrdinaryFolderCopyDropsTheDesktopFootprint() {
        assertTrue(LargeFolderFootprintPolicy.normalizeAllAppsCopy(true, -104L, 2, 2, 2));
        assertFalse(LargeFolderFootprintPolicy.normalizeAllAppsCopy(true, -100L, 21, 2, 2));
        assertFalse(LargeFolderFootprintPolicy.normalizeAllAppsCopy(false, -104L, 2, 2, 2));
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
