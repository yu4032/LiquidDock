package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.concurrent.*;

public class WallpaperNativeTransformStoreTest {
    @Test
    public void firstCaptureWaitsForNativeHistoryRatherThanAssumingNeutral() {
        WallpaperNativeTransformStore store = new WallpaperNativeTransformStore();
        assertNull(store.capture(0, 210, 10));
        store.record(0, new LauncherWallpaperTransformState.Sample(200, 1.14f, .5f, .4f));
        assertNull(store.capture(0, 0, 10));
        assertNull(store.capture(0, 190, 10));
        assertEquals(1.14f, store.capture(0, 210, 10).scale, 0f);
    }

    @Test
    public void queryWaitsForAnAppliedUpdateEvenWhenItsClientCallbackIsDelayed() throws Exception {
        WallpaperNativeTransformStore store = new WallpaperNativeTransformStore();
        store.record(0, new LauncherWallpaperTransformState.Sample(100, 1f, .5f, .5f));
        store.beginUpdate();
        FutureTask<LauncherWallpaperTransformState.Sample> query =
                new FutureTask<>(() -> store.capture(0, 210, 1000));
        Thread worker = new Thread(query);
        worker.start();
        try {
            assertThrows(TimeoutException.class, () -> query.get(20, TimeUnit.MILLISECONDS));
            store.record(0, new LauncherWallpaperTransformState.Sample(200, 1.14f, .5f, .5f));
        } finally {
            store.endUpdate();
        }
        assertEquals(1.14f, query.get(1, TimeUnit.SECONDS).scale, 0f);
        worker.join();
    }

    @Test
    public void stableCaptureNeedsNoFutureAnimationCallback() {
        WallpaperNativeTransformStore store = new WallpaperNativeTransformStore();
        store.record(0, new LauncherWallpaperTransformState.Sample(100, 1f, .5f, .5f));
        assertEquals(1f, store.capture(0, 200, 10).scale, 0f);
        assertNull(store.capture(1, 200, 10));
    }

    @Test
    public void incompleteNativeUpdateTimesOutRatherThanGuessingMetadata() {
        WallpaperNativeTransformStore store = new WallpaperNativeTransformStore();
        store.record(0, new LauncherWallpaperTransformState.Sample(100, 1f, .5f, .5f));
        store.beginUpdate();
        try {
            assertNull(store.capture(0, 200, 5));
        } finally {
            store.endUpdate();
        }
        assertNotNull(store.capture(0, 200, 5));
    }
}
