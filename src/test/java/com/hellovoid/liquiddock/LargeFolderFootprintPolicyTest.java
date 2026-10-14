package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class LargeFolderFootprintPolicyTest {
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
