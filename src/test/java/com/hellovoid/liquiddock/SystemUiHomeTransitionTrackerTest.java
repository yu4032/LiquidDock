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

    @Test public void unstartedMergedHomeSourcePublishesStartAtMergeAndFinishesOnTarget() {
        SystemUiHomeTransitionTracker tracker = new SystemUiHomeTransitionTracker();
        Object source = new Object();
        Object target = new Object();

        tracker.beginReady(source);
        tracker.recordCurrentReadyVisibility(true);
        tracker.endReady();

        SystemUiHomeTransitionTracker.Event mergedStart = tracker.onMerged(source, target);
        assertNotNull(mergedStart);
        assertTrue(mergedStart.homeVisible());
        assertTrue(mergedStart.serial() > 0L);
        assertEquals(null, tracker.onStarting(target));

        SystemUiHomeTransitionTracker.Event finish = tracker.onFinished(target);
        assertNotNull(finish);
        assertTrue(finish.homeVisible());
        assertEquals(mergedStart.serial(), finish.serial());
    }

    @Test public void newerMergedHomeSourceSupersedesOlderPlayingTargetClassification() {
        SystemUiHomeTransitionTracker tracker = new SystemUiHomeTransitionTracker();
        Object target = new Object();
        Object source = new Object();

        tracker.beginReady(target);
        tracker.recordCurrentReadyVisibility(false);
        tracker.endReady();
        SystemUiHomeTransitionTracker.Event oldStart = tracker.onStarting(target);
        assertNotNull(oldStart);
        assertFalse(oldStart.homeVisible());

        tracker.beginReady(source);
        tracker.recordCurrentReadyVisibility(true);
        tracker.endReady();
        SystemUiHomeTransitionTracker.Event mergedStart = tracker.onMerged(source, target);

        assertNotNull(mergedStart);
        assertTrue(mergedStart.homeVisible());
        assertTrue(mergedStart.serial() > oldStart.serial());

        SystemUiHomeTransitionTracker.Event finish = tracker.onFinished(target);
        assertNotNull(finish);
        assertTrue(finish.homeVisible());
        assertEquals(mergedStart.serial(), finish.serial());
    }

    @Test public void targetFinishUsesSameSerialPublishedAtMerge() {
        SystemUiHomeTransitionTracker tracker = new SystemUiHomeTransitionTracker();
        Object source = new Object();
        Object target = new Object();

        tracker.beginReady(source);
        tracker.recordCurrentReadyVisibility(true);
        tracker.endReady();
        SystemUiHomeTransitionTracker.Event mergedStart = tracker.onMerged(source, target);
        SystemUiHomeTransitionTracker.Event finish = tracker.onFinished(target);

        assertNotNull(mergedStart);
        assertNotNull(finish);
        assertEquals(mergedStart.serial(), finish.serial());
        assertEquals(mergedStart.homeVisible(), finish.homeVisible());
    }

    @Test public void alreadyStartedSourceDoesNotPublishDuplicateStartWhenMerged() {
        SystemUiHomeTransitionTracker tracker = new SystemUiHomeTransitionTracker();
        Object source = new Object();
        Object target = new Object();

        tracker.beginReady(source);
        tracker.recordCurrentReadyVisibility(true);
        tracker.endReady();
        SystemUiHomeTransitionTracker.Event start = tracker.onStarting(source);
        assertNotNull(start);

        assertEquals(null, tracker.onMerged(source, target));
        SystemUiHomeTransitionTracker.Event finish = tracker.onFinished(target);
        assertNotNull(finish);
        assertEquals(start.serial(), finish.serial());
    }
}
