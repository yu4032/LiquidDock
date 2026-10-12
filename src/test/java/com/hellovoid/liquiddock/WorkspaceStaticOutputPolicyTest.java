package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class WorkspaceStaticOutputPolicyTest {
    @Test public void firstEmptySurfaceStillClearsAndSwaps() {
        assertTrue(WorkspaceStaticOutputPolicy.shouldRender(false, false));
    }

    @Test public void repeatedBlankScenesAvoidFullSizeSwap() {
        assertFalse(WorkspaceStaticOutputPolicy.shouldRender(true, false));
    }

    @Test public void anyDrawableGlassAlwaysRendersEvenAfterBlank() {
        assertTrue(WorkspaceStaticOutputPolicy.shouldRender(true, true));
        assertTrue(WorkspaceStaticOutputPolicy.shouldRender(false, true));
    }

    @Test public void disappearingLastWidgetStillClearsPreviousContent() {
        // The last content swap leaves blankPresented=false. A following all-zero
        // scene *must* clear once; only a successful swap may mark it blank.
        assertTrue(WorkspaceStaticOutputPolicy.shouldRender(false, false));
        assertFalse(WorkspaceStaticOutputPolicy.shouldRender(true, false));
    }
}
