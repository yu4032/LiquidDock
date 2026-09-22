package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WallpaperChangeIdentityStateTest {
    @Test public void initialWallpaperIdDoesNotLookLikeAChange() {
        WallpaperChangeIdentityState state = new WallpaperChangeIdentityState();
        state.initialize(41);
        assertFalse(state.shouldAdvance(41));
        assertEquals(41, state.lastWallpaperId());
    }

    @Test public void newWallpaperIdAdvancesExactlyOnceAcrossDuplicateAuthorities() {
        WallpaperChangeIdentityState state = new WallpaperChangeIdentityState();
        state.initialize(41);
        assertTrue(state.shouldAdvance(42));
        assertFalse(state.shouldAdvance(42));
        assertEquals(42, state.lastWallpaperId());
    }

    @Test public void unknownWallpaperIdFailsOpenForLiveOrDefaultWallpaper() {
        WallpaperChangeIdentityState state = new WallpaperChangeIdentityState();
        state.initialize(41);
        assertTrue(state.shouldAdvance(-1));
        assertTrue(state.shouldAdvance(-1));
        assertEquals(41, state.lastWallpaperId());
    }
}
