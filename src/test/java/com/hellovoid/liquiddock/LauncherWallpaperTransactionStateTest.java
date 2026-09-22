package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LauncherWallpaperTransactionStateTest {
    @Test public void oneVendorCallbackAuthorizesMatchingTaskCompletion() {
        LauncherWallpaperTransactionState state = new LauncherWallpaperTransactionState();
        long changed = state.onWallpaperChanged();
        long task = state.onTaskStarted();

        assertEquals(changed, task);
        assertTrue(state.shouldPublishTaskCompletion(task));
    }

    @Test public void queuedCallbacksCollapseToNewestVendorGeneration() {
        LauncherWallpaperTransactionState state = new LauncherWallpaperTransactionState();
        state.onWallpaperChanged();
        long latest = state.onWallpaperChanged();

        long task = state.onTaskStarted();

        assertEquals(latest, task);
        assertTrue(state.shouldPublishTaskCompletion(task));
    }

    @Test public void runningOldTaskCannotAuthorizeNewerWallpaper() {
        LauncherWallpaperTransactionState state = new LauncherWallpaperTransactionState();
        long old = state.onWallpaperChanged();
        long oldTask = state.onTaskStarted();

        long latest = state.onWallpaperChanged();

        assertEquals(old, oldTask);
        assertFalse(state.shouldPublishTaskCompletion(oldTask));

        long latestTask = state.onTaskStarted();
        assertEquals(latest, latestTask);
        assertTrue(state.shouldPublishTaskCompletion(latestTask));
    }
}
