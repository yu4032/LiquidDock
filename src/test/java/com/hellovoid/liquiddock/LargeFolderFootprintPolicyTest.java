package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LargeFolderFootprintPolicyTest {
    @Test public void requiresVendorFourPreviewTypeAndRealOccupancy() {
        assertTrue(LargeFolderFootprintPolicy.hasRealTwoByTwoFootprint(21, 2, 2));
        assertFalse(LargeFolderFootprintPolicy.hasRealTwoByTwoFootprint(21, 1, 1));
        assertFalse(LargeFolderFootprintPolicy.hasRealTwoByTwoFootprint(22, 2, 2));
        assertFalse(LargeFolderFootprintPolicy.hasRealTwoByTwoFootprint(2, 2, 2));
    }
}
