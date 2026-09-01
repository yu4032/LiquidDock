package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.Test;

/** Pure token lifecycle coverage for SystemUI HomeTransitionObserver timing handoff. */
public class SystemUiHomeTransitionTrackerTest {
    private static Object tracker() throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.hellovoid.liquiddock.SystemUiHomeTransitionTracker");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("missing SystemUiHomeTransitionTracker", missing);
        }
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static boolean eventBoolean(Object event, String name) throws Exception {
        Method method = event.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return (Boolean) method.invoke(event);
    }

    private static long eventLong(Object event, String name) throws Exception {
        Method method = event.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return (Long) method.invoke(event);
    }

    @Test public void readyVisibilityIsPublishedAtMatchingStartAndFinish() throws Exception {
        Object tracker = tracker();
        Object token = new Object();

        call(tracker, "beginReady", new Class<?>[]{Object.class}, token);
        call(tracker, "recordCurrentReadyVisibility", new Class<?>[]{boolean.class}, true);
        call(tracker, "endReady", new Class<?>[0]);

        Object start = call(tracker, "onStarting", new Class<?>[]{Object.class}, token);
        assertNotNull(start);
        assertTrue(eventBoolean(start, "homeVisible"));
        assertTrue(eventLong(start, "serial") > 0L);

        Object duplicateStart = call(tracker, "onStarting", new Class<?>[]{Object.class}, token);
        assertEquals(null, duplicateStart);

        Object finish = call(tracker, "onFinished", new Class<?>[]{Object.class}, token);
        assertNotNull(finish);
        assertTrue(eventBoolean(finish, "homeVisible"));
        assertEquals(eventLong(start, "serial"), eventLong(finish, "serial"));
        assertEquals(null, call(tracker, "onFinished", new Class<?>[]{Object.class}, token));
    }

    @Test public void closingHomeTransitionPreservesFalseVisibility() throws Exception {
        Object tracker = tracker();
        Object token = new Object();

        call(tracker, "beginReady", new Class<?>[]{Object.class}, token);
        call(tracker, "recordCurrentReadyVisibility", new Class<?>[]{boolean.class}, false);
        call(tracker, "endReady", new Class<?>[0]);

        Object start = call(tracker, "onStarting", new Class<?>[]{Object.class}, token);
        assertNotNull(start);
        assertFalse(eventBoolean(start, "homeVisible"));
    }

    @Test public void mergedTransitionKeepsOriginalSerialOnTargetToken() throws Exception {
        Object tracker = tracker();
        Object source = new Object();
        Object target = new Object();

        call(tracker, "beginReady", new Class<?>[]{Object.class}, source);
        call(tracker, "recordCurrentReadyVisibility", new Class<?>[]{boolean.class}, true);
        call(tracker, "endReady", new Class<?>[0]);
        call(tracker, "onMerged", new Class<?>[]{Object.class, Object.class}, source, target);

        Object start = call(tracker, "onStarting", new Class<?>[]{Object.class}, target);
        assertNotNull(start);
        long serial = eventLong(start, "serial");
        assertTrue(serial > 0L);
        Object finish = call(tracker, "onFinished", new Class<?>[]{Object.class}, target);
        assertNotNull(finish);
        assertEquals(serial, eventLong(finish, "serial"));
    }

    @Test public void visibilityOutsideReadyContextIsIgnored() throws Exception {
        Object tracker = tracker();
        call(tracker, "recordCurrentReadyVisibility", new Class<?>[]{boolean.class}, true);
        assertEquals(null, call(tracker, "onStarting", new Class<?>[]{Object.class}, new Object()));
    }
}
