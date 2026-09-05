package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.Test;

public class DockFrameCommitHandoffStateTest {
    @Test public void completionIsExactlyOnce() throws Exception {
        Object state = newState();
        assertTrue(completeIfPending(state));
        assertFalse(completeIfPending(state));
    }

    @Test public void cancelledFallbackRejectsLateFrameCommit() throws Exception {
        Object state = newState();
        cancel(state);
        assertFalse(completeIfPending(state));
        cancel(state);
        assertFalse(completeIfPending(state));
    }

    private static Object newState() throws Exception {
        try {
            Class<?> type = Class.forName(
                    "com.hellovoid.liquiddock.DockFrameCommitHandoffState");
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ClassNotFoundException missing) {
            fail("missing runtime DockFrameCommitHandoffState");
            throw missing;
        }
    }

    private static boolean completeIfPending(Object state) throws Exception {
        Method method = state.getClass().getDeclaredMethod("completeIfPending");
        method.setAccessible(true);
        return (Boolean) method.invoke(state);
    }

    private static void cancel(Object state) throws Exception {
        Method method = state.getClass().getDeclaredMethod("cancel");
        method.setAccessible(true);
        method.invoke(state);
    }
}
