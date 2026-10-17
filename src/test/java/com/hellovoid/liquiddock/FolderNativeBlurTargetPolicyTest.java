package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FolderNativeBlurTargetPolicyTest {
    private static final float EPS = 0.0001f;
    private static final float FOLDER_BLUR = 0.22f;

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

    @Test public void regularFolderOpeningUsesTheSameCap() {
        assertEquals(FOLDER_BLUR,
                FolderNativeBlurTargetPolicy.capWhenFolderOwns(
                        1f, true, false, FOLDER_BLUR), EPS);
        assertEquals(0f,
                FolderNativeBlurTargetPolicy.capWhenFolderOwns(
                        0f, true, false, FOLDER_BLUR), EPS);
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
