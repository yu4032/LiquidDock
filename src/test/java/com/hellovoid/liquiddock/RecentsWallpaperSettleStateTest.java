package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Host-side serial/freshness authority tests for Recents -> HOME wallpaper settling. */
public class RecentsWallpaperSettleStateTest {
    @Test
    public void recentsShowInvalidatesOlderReturnCallback() {
        RecentsWallpaperSettleState state = new RecentsWallpaperSettleState();
        long oldReturn = state.onReturnStarted();

        long showSerial = state.onRecentsShown();

        assertNotEquals(oldReturn, showSerial);
        assertFalse(state.isPending());
        assertFalse(state.onWallpaperSettled(oldReturn));
    }

    @Test
    public void hideRemainsPendingUntilRealSettledEvent() {
        RecentsWallpaperSettleState state = new RecentsWallpaperSettleState();

        long serial = state.onReturnStarted();

        assertTrue(state.isPending());
        assertTrue(state.pendingSerial() == serial);
        assertFalse("no wall-clock path exists in this state machine", !state.isPending());
        assertTrue(state.onWallpaperSettled(serial));
        assertFalse(state.isPending());
    }

    @Test
    public void staleSettledEventCannotReleaseCurrentReturn() {
        RecentsWallpaperSettleState state = new RecentsWallpaperSettleState();
        long first = state.onReturnStarted();
        state.onRecentsShown();
        long second = state.onReturnStarted();

        assertFalse(state.onWallpaperSettled(first));
        assertTrue(state.isPending());
        assertTrue(state.onWallpaperSettled(second));
        assertFalse(state.isPending());
    }

    @Test
    public void consecutiveReturnCyclesRejectOlderCompletion() {
        RecentsWallpaperSettleState state = new RecentsWallpaperSettleState();

        long firstReturn = state.onReturnStarted();
        state.onRecentsShown();
        long secondReturn = state.onReturnStarted();
        state.onRecentsShown();
        long thirdReturn = state.onReturnStarted();

        assertFalse(state.onWallpaperSettled(firstReturn));
        assertFalse(state.onWallpaperSettled(secondReturn));
        assertTrue(state.isPending());
        assertTrue(state.onWallpaperSettled(thirdReturn));
        assertFalse(state.isPending());
    }

    @Test
    public void rejectedWorkstationReturnCanCancelOnlyCurrentSerial() {
        RecentsWallpaperSettleState state = new RecentsWallpaperSettleState();
        long first = state.onReturnStarted();
        state.onRecentsShown();
        long second = state.onReturnStarted();

        assertFalse(state.cancelReturn(first));
        assertTrue(state.isPending());
        assertTrue(state.cancelReturn(second));
        assertFalse(state.isPending());
        assertFalse(state.onWallpaperSettled(second));
    }
}
