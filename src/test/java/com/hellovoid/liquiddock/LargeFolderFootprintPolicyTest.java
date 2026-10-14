package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Native footprint conversion is invoked only for valid persisted 1x1 model mismatches. */
public class LargeFolderFootprintPolicyTest {
    @Test public void persistedCompactLargeFolderNeedsTrueTwoByTwoConversion() {
        assertTrue(LargeFolderFootprintPolicy.needsNativeConversion(
                21, 1, 1, 123, 42, -100));
        assertFalse(LargeFolderFootprintPolicy.needsNativeConversion(
                21, 2, 2, 123, 42, -100));
        assertFalse(LargeFolderFootprintPolicy.needsNativeConversion(
                21, 1, 2, 123, 42, -100));
        assertFalse(LargeFolderFootprintPolicy.needsNativeConversion(
                2, 1, 1, 123, 42, -100));
        assertFalse(LargeFolderFootprintPolicy.needsNativeConversion(
                22, 1, 1, 123, 42, -100));
    }

    @Test public void conversionCannotTouchDockOrUnpersistedFolders() {
        assertFalse(LargeFolderFootprintPolicy.needsNativeConversion(
                21, 1, 1, 123, 42, -101));
        assertFalse(LargeFolderFootprintPolicy.needsNativeConversion(
                21, 1, 1, -1, 42, -100));
        assertFalse(LargeFolderFootprintPolicy.needsNativeConversion(
                21, 1, 1, 123, -1, -100));
    }


    @Test public void onlyFourItemSmallWorkspaceFolderMayBePromoted() {
        assertTrue(LargeFolderFootprintPolicy.needsNativePromotion(
                2, 1, 1, 4, 123, 42, -100));
        assertFalse(LargeFolderFootprintPolicy.needsNativePromotion(
                2, 1, 1, 3, 123, 42, -100));
        assertFalse(LargeFolderFootprintPolicy.needsNativePromotion(
                2, 1, 1, 5, 123, 42, -100));
        assertFalse(LargeFolderFootprintPolicy.needsNativePromotion(
                21, 1, 1, 4, 123, 42, -100));
        assertFalse(LargeFolderFootprintPolicy.needsNativePromotion(
                2, 2, 2, 4, 123, 42, -100));
        assertFalse(LargeFolderFootprintPolicy.needsNativePromotion(
                2, 1, 1, 4, 123, 42, -101));
        assertFalse(LargeFolderFootprintPolicy.needsNativePromotion(
                2, 1, 1, 4, -1, 42, -100));
    }

    @Test public void renderingRequiresActualTwoCellModelSpans() {
        assertTrue(LargeFolderFootprintPolicy.hasRealTwoByTwoFootprint(21, 2, 2));
        assertFalse(LargeFolderFootprintPolicy.hasRealTwoByTwoFootprint(21, 1, 1));
        assertFalse(LargeFolderFootprintPolicy.hasRealTwoByTwoFootprint(22, 2, 2));
        assertFalse(LargeFolderFootprintPolicy.hasRealTwoByTwoFootprint(2, 2, 2));
    }
}
