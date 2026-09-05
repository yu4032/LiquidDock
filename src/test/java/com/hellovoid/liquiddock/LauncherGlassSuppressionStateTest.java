package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.junit.Test;

public class LauncherGlassSuppressionStateTest {
    @Test public void folderAndDragSuppressionComposeWithoutOverwritingEachOther() {
        LauncherGlassSuppressionState state = new LauncherGlassSuppressionState();

        assertFalse(state.isSuppressed());
        assertTrue(state.setFolderOpen(true));
        assertTrue(state.isSuppressed());

        assertTrue(state.setDrag(true));
        assertTrue(state.isSuppressed());

        assertTrue(state.setFolderOpen(false));
        assertTrue("drag still owns suppression", state.isSuppressed());

        assertTrue(state.setDrag(false));
        assertFalse(state.isSuppressed());
    }

    @Test public void repeatedOwnerEventIsIdempotent() {
        LauncherGlassSuppressionState state = new LauncherGlassSuppressionState();
        assertTrue(state.setDrag(true));
        assertFalse(state.setDrag(true));
        assertTrue(state.isSuppressed());
    }

    @Test public void reattachVisibilityRespectsEveryActiveSuppressionOwner() throws Exception {
        LauncherGlassSuppressionState state = new LauncherGlassSuppressionState();
        assertTrue(shouldShowOnAttach(state));

        state.setDrag(true);
        assertFalse(shouldShowOnAttach(state));

        state.setFolderOpen(true);
        state.setDrag(false);
        assertFalse("folder-open still owns suppression", shouldShowOnAttach(state));

        state.setFolderOpen(false);
        assertTrue(shouldShowOnAttach(state));
    }

    private static boolean shouldShowOnAttach(LauncherGlassSuppressionState state) throws Exception {
        try {
            Method method = LauncherGlassSuppressionState.class.getDeclaredMethod("shouldShowOnAttach");
            method.setAccessible(true);
            return (Boolean) method.invoke(state);
        } catch (NoSuchMethodException missing) {
            fail("missing runtime LauncherGlassSuppressionState.shouldShowOnAttach");
            throw missing;
        }
    }
}
