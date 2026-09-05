package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure token lifecycle coverage for SystemUI HomeTransitionObserver timing handoff. */
public class SystemUiHomeTransitionTrackerTest {
    @Test public void readyVisibilityIsPublishedAtMatchingStartAndFinish() {
        SystemUiHomeTransitionTracker tracker = new SystemUiHomeTransitionTracker();
        Object token = new Object();

        tracker.beginReady(token);
        tracker.recordCurrentReadyVisibility(true);
        tracker.endReady();

        SystemUiHomeTransitionTracker.Event start = tracker.onStarting(token);
        assertNotNull(start);
        assertTrue(start.homeVisible());
        assertTrue(start.serial() > 0L);

        assertEquals(null, tracker.onStarting(token));

        SystemUiHomeTransitionTracker.Event finish = tracker.onFinished(token);
        assertNotNull(finish);
        assertTrue(finish.homeVisible());
        assertEquals(start.serial(), finish.serial());
        assertEquals(null, tracker.onFinished(token));
    }

    @Test public void closingHomeTransitionPreservesFalseVisibility() {
        SystemUiHomeTransitionTracker tracker = new SystemUiHomeTransitionTracker();
        Object token = new Object();

        tracker.beginReady(token);
        tracker.recordCurrentReadyVisibility(false);
        tracker.endReady();

        SystemUiHomeTransitionTracker.Event start = tracker.onStarting(token);
        assertNotNull(start);
        assertFalse(start.homeVisible());
    }

    @Test public void mergedTransitionKeepsOriginalSerialOnTargetToken() {
        SystemUiHomeTransitionTracker tracker = new SystemUiHomeTransitionTracker();
        Object source = new Object();
        Object target = new Object();

        tracker.beginReady(source);
        tracker.recordCurrentReadyVisibility(true);
        tracker.endReady();
        tracker.onMerged(source, target);

        SystemUiHomeTransitionTracker.Event start = tracker.onStarting(target);
        assertNotNull(start);
        long serial = start.serial();
        assertTrue(serial > 0L);
        SystemUiHomeTransitionTracker.Event finish = tracker.onFinished(target);
        assertNotNull(finish);
        assertEquals(serial, finish.serial());
    }

    @Test public void visibilityOutsideReadyContextIsIgnored() {
        SystemUiHomeTransitionTracker tracker = new SystemUiHomeTransitionTracker();
        tracker.recordCurrentReadyVisibility(true);
        assertEquals(null, tracker.onStarting(new Object()));
    }
}
