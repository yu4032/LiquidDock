package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The native Recents compositor remains the sole full-screen wallpaper owner. */
public class OpenFolderRecentsCompositionPolicyTest {
    @Test public void ordinaryExpandedFolderStillHasItsFullFrozenBackground() {
        assertTrue(OpenFolderRecentsCompositionPolicy.drawFrozenWallpaper(true, false));
    }

    @Test public void recentsRetainsOnlyTheOpticalLensNotASecondWallpaper() {
        assertFalse(OpenFolderRecentsCompositionPolicy.drawFrozenWallpaper(true, true));
    }

    @Test public void shortcutPopupNeverInheritsOpenFolderBackdropOwnership() {
        assertFalse(OpenFolderRecentsCompositionPolicy.drawFrozenWallpaper(false, false));
        assertFalse(OpenFolderRecentsCompositionPolicy.drawFrozenWallpaper(false, true));
    }

    @Test public void returningFromRecentsRestoresExpandedFolderBackdrop() {
        boolean recents = true;
        assertFalse(OpenFolderRecentsCompositionPolicy.drawFrozenWallpaper(true, recents));
        recents = false; // only after the EGL return frame is ready
        assertTrue(OpenFolderRecentsCompositionPolicy.drawFrozenWallpaper(true, recents));
    }
}
