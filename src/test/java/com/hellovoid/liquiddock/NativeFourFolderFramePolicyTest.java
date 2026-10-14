package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

/** Contracts the real 2-cell allocation without depending on Launcher View lifecycles. */
public class NativeFourFolderFramePolicyTest {
    @Test public void resolvesRectangularNativeAllocationWithoutSquaring() {
        NativeFourFolderFramePolicy.Frame frame = NativeFourFolderFramePolicy.resolve(
                new int[]{0, 359, 718}, new int[]{0, 362, 724},
                0, 0, 359, 359, 800, 800, 0, 0, 0, 0);
        assertNotNull(frame);
        assertEquals(718, frame.width);
        assertEquals(721, frame.height);
    }

    @Test public void respectsMarginsSymmetricallyAndTracksPageMove() {
        NativeFourFolderFramePolicy.Frame frame = NativeFourFolderFramePolicy.resolve(
                new int[]{12, 371, 730}, new int[]{20, 382, 744},
                0, 0, 359, 359, 900, 900, 10, 6, 10, 4);
        assertNotNull(frame);
        assertEquals(22, frame.left);
        assertEquals(25, frame.top);
        assertEquals(698, frame.width);
        assertEquals(711, frame.height);
        NativeFourFolderFramePolicy.Frame moved = NativeFourFolderFramePolicy.resolve(
                new int[]{12, 371, 730}, new int[]{20, 382, 744},
                1, 1, 359, 359, 1200, 1200, 0, 0, 0, 0);
        assertNotNull(moved);
        assertEquals(371, moved.left);
        assertEquals(382, moved.top);
    }

    @Test public void failsClosedForDragFullscreenOrUncommittedGrid() {
        assertNull(NativeFourFolderFramePolicy.resolve(new int[]{0, 359},
                new int[]{0, 362}, 0, 0, 359, 359, 500, 500, 0, 0, 0, 0));
        assertNull(NativeFourFolderFramePolicy.resolve(new int[]{0},
                new int[]{0, 362}, 0, 0, 359, 359, 800, 800, 0, 0, 0, 0));
        assertNull(NativeFourFolderFramePolicy.resolve(new int[]{0, 359},
                new int[]{0, 362}, 0, 0, 359, 359, 0, 800, 0, 0, 0, 0));
        assertNull(NativeFourFolderFramePolicy.resolve(new int[]{0, 359},
                new int[]{0, 362}, 1, 0, 359, 359, 1000, 1000, 0, 0, 0, 0));
    }

    @Test public void firstMeasureUsesRealTwoCellFrameBeforeParentLayout() {
        // Fresh Launcher restore: CellLayout has built its mXs/mYs but
        // has not yet received a final nonzero measured frame.
        NativeFourFolderFramePolicy.Frame early =
                NativeFourFolderFramePolicy.forInitialMeasure(
                        new int[]{0, 359, 718}, new int[]{0, 362, 724},
                        0, 0, 359, 359, 0, 0, 0, 0);
        assertNotNull(early);
        assertEquals(718, early.width);
        assertEquals(721, early.height);
        // The post-layout authority still rejects a parent with no bounds.
        assertNull(NativeFourFolderFramePolicy.resolve(
                new int[]{0, 359, 718}, new int[]{0, 362, 724},
                0, 0, 359, 359, 0, 0, 0, 0, 0, 0));
    }

    @Test public void portraitAndLandscapeCalculateFromTheirOwnGrid() {
        NativeFourFolderFramePolicy.Frame portrait = NativeFourFolderFramePolicy.resolve(
                new int[]{0, 360}, new int[]{0, 358},
                0, 0, 360, 358, 800, 800, 0, 0, 0, 0);
        NativeFourFolderFramePolicy.Frame landscape = NativeFourFolderFramePolicy.resolve(
                new int[]{0, 420}, new int[]{0, 310},
                0, 0, 420, 310, 900, 700, 0, 0, 0, 0);
        assertEquals(720, portrait.width);
        assertEquals(716, portrait.height);
        assertEquals(840, landscape.width);
        assertEquals(620, landscape.height);
    }
}
