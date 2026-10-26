package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

/** Pure-JVM native-alpha ownership contracts; no Android View or framework required. */
public class GestureHandleAlphaStateTest {
    private static final class Write {
        final Object handle;
        final float alpha;

        Write(Object handle, float alpha) {
            this.handle = handle;
            this.alpha = alpha;
        }
    }

    @Test
    public void handleCapturedDuringHiddenSceneIsImmediatelyHidden() {
        GestureHandleAlphaState<Object> state = new GestureHandleAlphaState<>();
        Object first = new Object();
        Object second = new Object();
        List<Write> writes = new ArrayList<>();
        GestureHandleAlphaState.Writer<Object> writer = (handle, alpha) -> {
            writes.add(new Write(handle, alpha));
            return true;
        };
        assertTrue(state.capture(first, 0.75f, writer));
        assertTrue(state.setHidden(true, writer));
        assertEquals(1, writes.size());
        assertTrue(state.capture(second, 0.6f, writer));
        assertEquals(2, writes.size());
        assertTrue(writes.get(1).handle == second);
        assertEquals(0f, writes.get(1).alpha, 0f);
        assertTrue(state.setHidden(false, writer));
        assertEquals(4, writes.size());
    }

    @Test
    public void hiddenVendorWritesUpdateRestoreTargetWithoutBecomingVisible() {
        GestureHandleAlphaState<Object> state = new GestureHandleAlphaState<>();
        Object handle = new Object();
        List<Float> writes = new ArrayList<>();
        GestureHandleAlphaState.Writer<Object> writer = (h, alpha) -> {
            writes.add(alpha);
            return true;
        };
        state.capture(handle, 1f, writer);
        state.setHidden(true, writer);
        assertEquals(0f, state.onVendorAlpha(handle, 0.35f), 0f);
        state.setHidden(false, writer);
        assertEquals(0.35f, writes.get(writes.size() - 1), 0f);
    }

    @Test
    public void failedRestoreRetainsOnlyUnrestoredNativeHandleForRetry() {
        GestureHandleAlphaState<Object> state = new GestureHandleAlphaState<>();
        Object good = new Object();
        Object bad = new Object();
        state.capture(good, 0.4f, (h, alpha) -> true);
        state.capture(bad, 0.7f, (h, alpha) -> true);
        AtomicBoolean allowBad = new AtomicBoolean(false);
        GestureHandleAlphaState.Writer<Object> writer =
                (h, alpha) -> h != bad || allowBad.get();
        assertFalse(state.restoreAndClear(writer));
        assertEquals(1, state.trackedCount());
        allowBad.set(true);
        assertTrue(state.restoreAndClear(writer));
        assertEquals(0, state.trackedCount());
    }

    @Test
    public void failedVisibilityWriteIsRetriedEvenWhenSceneDoesNotChange() {
        GestureHandleAlphaState<Object> state = new GestureHandleAlphaState<>();
        Object handle = new Object();
        state.capture(handle, 0.9f, (h, alpha) -> true);
        AtomicBoolean succeeds = new AtomicBoolean(false);
        assertFalse(state.setHidden(true, (h, alpha) -> succeeds.get()));
        succeeds.set(true);
        List<Float> retried = new ArrayList<>();
        assertTrue(state.setHidden(true, (h, alpha) -> {
            retried.add(alpha);
            return true;
        }));
        assertEquals(1, retried.size());
        assertEquals(0f, retried.get(0), 0f);
    }
}
