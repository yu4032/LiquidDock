package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Typed runtime policy for drag-time producer/render decisions. */
public class GboardFloatingDragPerformanceContractTest {
    @Test public void positionOnlyGeometryNeverReconcilesProducer() {
        assertFalse(GboardFloatingUpdatePolicy.shouldReconcileRoot(
                GboardFloatingUpdatePolicy.Cause.GEOMETRY));
        assertTrue(GboardFloatingUpdatePolicy.shouldRequestRender(
                GboardFloatingUpdatePolicy.Cause.GEOMETRY));
    }

    @Test public void freshFramesAndOutputResizeRenderWithoutRootReconcile() {
        assertFalse(GboardFloatingUpdatePolicy.shouldReconcileRoot(
                GboardFloatingUpdatePolicy.Cause.FRESH_FRAME));
        assertTrue(GboardFloatingUpdatePolicy.shouldRequestRender(
                GboardFloatingUpdatePolicy.Cause.FRESH_FRAME));
        assertFalse(GboardFloatingUpdatePolicy.shouldReconcileRoot(
                GboardFloatingUpdatePolicy.Cause.OUTPUT_RESIZE));
        assertTrue(GboardFloatingUpdatePolicy.shouldRequestRender(
                GboardFloatingUpdatePolicy.Cause.OUTPUT_RESIZE));
    }

    @Test public void onlyInitialCaptureReconcilesProducer() {
        assertTrue(GboardFloatingUpdatePolicy.shouldReconcileRoot(
                GboardFloatingUpdatePolicy.Cause.INITIAL_CAPTURE));
        assertFalse(GboardFloatingUpdatePolicy.shouldRequestRender(
                GboardFloatingUpdatePolicy.Cause.INITIAL_CAPTURE));
    }
}
