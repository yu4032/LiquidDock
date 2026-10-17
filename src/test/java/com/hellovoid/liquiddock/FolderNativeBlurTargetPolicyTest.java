package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FolderNativeBlurTargetPolicyTest {
    private static final float EPS = 0.0001f;
    private static final float FOLDER_BLUR = 0.22f;

    @Test public void onlyHomeReturnToAnExistingExpandedFolderArmsEarlyBlurOwnership() {
        assertEquals(true, FolderNativeBlurTargetPolicy.shouldArmBeforeOverviewExit(true, true));
        assertEquals(false, FolderNativeBlurTargetPolicy.shouldArmBeforeOverviewExit(true, false));
        assertEquals(false, FolderNativeBlurTargetPolicy.shouldArmBeforeOverviewExit(false, true));
        assertEquals(false, FolderNativeBlurTargetPolicy.shouldArmBeforeOverviewExit(false, false));
    }

    @Test public void recentsReturnMustNotOvertakeExistingExpandedFolderBlur() {
        // On the affected Launcher, Recents HOME return writes fastBlur(1.0)
        // while the still-open folder's current sBlurRatioValue is 0.22.
        assertEquals(FOLDER_BLUR,
                FolderNativeBlurTargetPolicy.capWhenFolderOwns(
                        1f, false, true, FOLDER_BLUR), EPS);
        assertEquals(0.15f,
                FolderNativeBlurTargetPolicy.capWhenFolderOwns(
                        .15f, false, true, FOLDER_BLUR), EPS);
    }

    @Test public void nativeBlurPercentIsIndependentFromPrismalPixelRadius() {
        assertEquals(0f, FolderNativeBlurTargetPolicy.ratioFromPercent(0), EPS);
        assertEquals(.22f, FolderNativeBlurTargetPolicy.ratioFromPercent(22), EPS);
        assertEquals(.65f, FolderNativeBlurTargetPolicy.ratioFromPercent(65), EPS);
        assertEquals(1f, FolderNativeBlurTargetPolicy.ratioFromPercent(100), EPS);
        assertEquals(0f, FolderNativeBlurTargetPolicy.ratioFromPercent(-5), EPS);
        assertEquals(1f, FolderNativeBlurTargetPolicy.ratioFromPercent(200), EPS);

        assertEquals(.65f, FolderNativeBlurTargetPolicy.capWhenFolderOwns(
                1f, false, true, FolderNativeBlurTargetPolicy.ratioFromPercent(65)), EPS);
        assertEquals(0f, FolderNativeBlurTargetPolicy.capWhenFolderOwns(
                1f, true, false, FolderNativeBlurTargetPolicy.ratioFromPercent(0)), EPS);
    }

    @Test public void regularFolderOpeningUsesTheSameCap() {
        assertEquals(FOLDER_BLUR,
                FolderNativeBlurTargetPolicy.capWhenFolderOwns(
                        1f, true, false, FOLDER_BLUR), EPS);
        assertEquals(0f,
                FolderNativeBlurTargetPolicy.capWhenFolderOwns(
                        0f, true, false, FOLDER_BLUR), EPS);
    }

    @Test public void nativeFolderTargetWinsAgainstRecentsStrengthRegardlessOfScope() {
        assertEquals(.22f, FolderNativeBlurTargetPolicy.arbitrate(
                1f, true, 100, false, true, .22f), EPS);
        assertEquals(.15f, FolderNativeBlurTargetPolicy.arbitrate(
                1f, true, 15, false, true, .22f), EPS);
        assertEquals(.65f, FolderNativeBlurTargetPolicy.arbitrate(
                1f, true, 65, false, false, .22f), EPS);
        assertEquals(.81f, FolderNativeBlurTargetPolicy.arbitrate(
                .81f, false, 30, false, false, .22f), EPS);
        assertEquals(0f, FolderNativeBlurTargetPolicy.arbitrate(
                0f, true, 80, true, false, .22f), EPS);
        assertEquals(.33f, FolderNativeBlurTargetPolicy.arbitrate(
                1f, false, 100, true, false, .33f), EPS);
    }

    @Test public void recentsWithoutFolderAndNativeCloseRemainVendorOwned() {
        assertEquals(1f,
                FolderNativeBlurTargetPolicy.capWhenFolderOwns(
                        1f, false, false, FOLDER_BLUR), EPS);
        assertEquals(.81f,
                FolderNativeBlurTargetPolicy.capWhenFolderOwns(
                        .81f, false, false, FOLDER_BLUR), EPS);
        assertEquals(0f,
                FolderNativeBlurTargetPolicy.capWhenFolderOwns(
                        0f, false, false, FOLDER_BLUR), EPS);
    }
}
