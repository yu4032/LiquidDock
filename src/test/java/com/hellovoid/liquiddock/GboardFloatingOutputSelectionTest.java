package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GboardFloatingOutputSelectionTest {
    @Test public void preferredSurfaceControlWinsWhenReady() {
        GboardFloatingOutputSelection state = new GboardFloatingOutputSelection();
        assertEquals(GboardFloatingOutputSelection.Mode.SURFACE_CONTROL,
                state.onSurfaceControlReady());
    }

    @Test public void surfaceControlFailureFallsBackToTextureView() {
        GboardFloatingOutputSelection state = new GboardFloatingOutputSelection();
        assertEquals(GboardFloatingOutputSelection.Mode.TEXTURE_VIEW,
                state.onSurfaceControlFailed(true));
    }

    @Test public void failureWithoutTextureViewRestoresStock() {
        GboardFloatingOutputSelection state = new GboardFloatingOutputSelection();
        assertEquals(GboardFloatingOutputSelection.Mode.STOCK,
                state.onSurfaceControlFailed(false));
    }
}
