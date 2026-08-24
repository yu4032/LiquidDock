package com.hellovoid.liquiddock;

import static org.junit.Assert.assertSame;

import com.hellovoid.prismal.PrismalHighlightProfile;
import org.junit.Test;

public class LauncherHighlightProfilePolicyTest {
    private final PrismalHighlightProfile compact = new PrismalHighlightProfile(
            true, false, false, false, false, false, false, false, false);
    private final PrismalHighlightProfile large = new PrismalHighlightProfile(
            false, true, false, false, false, false, false, false, false);

    @Test public void iconsAndSmallFoldersUseCompactProfile() {
        assertSame(compact, LauncherHighlightProfilePolicy.select(
                LauncherGlassNodeKind.ICON, compact, large));
        assertSame(compact, LauncherHighlightProfilePolicy.select(
                LauncherGlassNodeKind.SMALL_FOLDER, compact, large));
    }

    @Test public void widgetsAndLargeFoldersUseLargeSurfaceProfile() {
        assertSame(large, LauncherHighlightProfilePolicy.select(
                LauncherGlassNodeKind.WIDGET, compact, large));
        assertSame(large, LauncherHighlightProfilePolicy.select(
                LauncherGlassNodeKind.LARGE_FOLDER, compact, large));
    }
}
