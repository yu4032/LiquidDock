package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FolderNativeBlurTargetPolicyTest {
    private static final float EPS = .0001f;

    @Test public void homeBlurWriteUsesNativeFolderOwnerBeforeRecentsHide() {
        assertEquals(true, FolderNativeBlurTargetPolicy.isCoveredRecentsHomeTarget(
                true, true, 1f));
        assertEquals(false, FolderNativeBlurTargetPolicy.isCoveredRecentsHomeTarget(
                true, false, 1f));
        assertEquals(false, FolderNativeBlurTargetPolicy.isCoveredRecentsHomeTarget(
                false, true, 1f));
        // The vendor HOME write may originate from a Recents wrapper; that
        // classification must not mask the observed 1.0 target.
        assertEquals(true, FolderNativeBlurTargetPolicy.isCoveredRecentsHomeTarget(
                true, true, 1f));
        assertEquals(false, FolderNativeBlurTargetPolicy.isCoveredRecentsHomeTarget(
                true, true, 0f));
        assertEquals(false, FolderNativeBlurTargetPolicy.isCoveredRecentsHomeTarget(
                true, true, .22f));
    }

    @Test public void nativeStrengthReadsPercentIndependentlyFromPrismalBlur() {
        assertEquals(0f, FolderNativeBlurTargetPolicy.ratioFromPercent(-3), EPS);
        assertEquals(0f, FolderNativeBlurTargetPolicy.ratioFromPercent(0), EPS);
        assertEquals(.22f, FolderNativeBlurTargetPolicy.ratioFromPercent(22), EPS);
        assertEquals(.75f, FolderNativeBlurTargetPolicy.ratioFromPercent(75), EPS);
        assertEquals(1f, FolderNativeBlurTargetPolicy.ratioFromPercent(100), EPS);
        assertEquals(1f, FolderNativeBlurTargetPolicy.ratioFromPercent(101), EPS);
    }

    @Test public void nativeOpenUsesConfiguredTargetIncludingAboveStockStrength() {
        assertEquals(.75f, FolderNativeBlurTargetPolicy.arbitrate(
                .22f, false, 100, true, false, .75f), EPS);
        assertEquals(.08f, FolderNativeBlurTargetPolicy.arbitrate(
                .22f, false, 100, true, false, .08f), EPS);
        assertEquals(0f, FolderNativeBlurTargetPolicy.arbitrate(
                .22f, false, 100, true, false, 0f), EPS);
    }

    @Test public void recentsReturnRestoresFolderConfiguredTargetNotTheHomeOne() {
        assertEquals(.22f, FolderNativeBlurTargetPolicy.arbitrate(
                1f, false, 100, false, true, .22f), EPS);
        assertEquals(.70f, FolderNativeBlurTargetPolicy.arbitrate(
                1f, true, 100, false, true, .70f), EPS);
        assertEquals(.70f, FolderNativeBlurTargetPolicy.arbitrate(
                1f, true, 10, false, true, .70f), EPS);
    }

    @Test public void nativeClearAndOrdinaryRecentsRemainVendorOwned() {
        assertEquals(0f, FolderNativeBlurTargetPolicy.arbitrate(
                0f, false, 100, false, true, .70f), EPS);
        assertEquals(0f, FolderNativeBlurTargetPolicy.arbitrate(
                0f, true, 100, true, false, .70f), EPS);
        assertEquals(1f, FolderNativeBlurTargetPolicy.arbitrate(
                1f, false, 100, false, false, .70f), EPS);
        assertEquals(.35f, FolderNativeBlurTargetPolicy.arbitrate(
                1f, true, 35, false, false, .70f), EPS);
        assertEquals(.91f, FolderNativeBlurTargetPolicy.arbitrate(
                .91f, false, 0, false, false, .70f), EPS);
    }
}
