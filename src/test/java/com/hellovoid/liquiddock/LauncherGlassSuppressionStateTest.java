package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
}
