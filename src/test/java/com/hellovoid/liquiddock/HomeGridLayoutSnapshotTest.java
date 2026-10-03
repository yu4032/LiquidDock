package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class HomeGridLayoutSnapshotTest {
    private static final HomeGridDimensions D8X4 = new HomeGridDimensions(8, 4);
    private static final HomeGridDimensions D10X6 = new HomeGridDimensions(10, 6);

    @Test public void orientationOtherIsSymmetric() {
        assertEquals(HomeGridOrientation.PORTRAIT, HomeGridOrientation.LANDSCAPE.other());
        assertEquals(HomeGridOrientation.LANDSCAPE, HomeGridOrientation.PORTRAIT.other());
    }

    @Test public void dimensionsTransposeForPortrait() {
        assertEquals(7, new HomeGridDimensions(7, 5).columns(HomeGridOrientation.LANDSCAPE));
        assertEquals(5, new HomeGridDimensions(7, 5).rows(HomeGridOrientation.LANDSCAPE));
        assertEquals(5, new HomeGridDimensions(7, 5).columns(HomeGridOrientation.PORTRAIT));
        assertEquals(7, new HomeGridDimensions(7, 5).rows(HomeGridOrientation.PORTRAIT));
    }

    @Test public void validMixedLayoutCreatesSnapshot() {
        HomeGridLayoutSnapshot snapshot = HomeGridLayoutSnapshot.create(
                D10X6, HomeGridOrientation.LANDSCAPE,
                Arrays.asList(pos(10,100,0,0,4,2), pos(11,100,4,0,2,2), pos(12,100,9,5,1,1)));
        assertNotNull(snapshot);
        assertEquals(3, snapshot.size());
    }

    @Test public void rejectsOutOfBoundsAndOverlap() {
        assertNull(HomeGridLayoutSnapshot.create(
                D8X4, HomeGridOrientation.LANDSCAPE,
                Arrays.asList(pos(1,0,7,3,2,1))));
        assertNull(HomeGridLayoutSnapshot.create(
                D8X4, HomeGridOrientation.LANDSCAPE,
                Arrays.asList(pos(1,1,0,0,4,2), pos(2,1,3,1,1,1))));
    }

    @Test public void sameCellsOnDifferentScreensAreAllowed() {
        assertNotNull(HomeGridLayoutSnapshot.create(
                D8X4, HomeGridOrientation.LANDSCAPE,
                Arrays.asList(pos(1,1,0,0,4,2), pos(2,2,0,0,4,2))));
    }

    private static HomeGridItemPosition pos(long id,long screen,int x,int y,int sx,int sy) {
        return new HomeGridItemPosition(id,screen,x,y,sx,sy);
    }
}
