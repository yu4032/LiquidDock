package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GboardHandwritingToolbarGeometryPolicyTest {
    @Test public void acceptsVerticalToolbar() {
        assertTrue(GboardHandwritingToolbarGeometryPolicy.isToolbar(84f, 286f, 1f));
    }

    @Test public void acceptsHorizontalToolbar() {
        assertTrue(GboardHandwritingToolbarGeometryPolicy.isToolbar(286f, 84f, 1f));
    }

    @Test public void rejectsFullKeyboardAndTinyViews() {
        assertFalse(GboardHandwritingToolbarGeometryPolicy.isToolbar(900f, 420f, 1f));
        assertFalse(GboardHandwritingToolbarGeometryPolicy.isToolbar(20f, 50f, 1f));
    }
}
