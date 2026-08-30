package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Regression for the corner-only ring exposed after the foreground-ring rewrite. */
public class DockStrokeSquircleGeometryTest {
    @Test public void legacyOutwardOffsetCannotPushStraightRingEdgesOutsideHost() {
        float[] insets = DockStrokeGeometry.resolveSquircleRingInsets(4f, 8f);

        assertEquals(0f, insets[0], 0.0001f);
        assertEquals(2f, insets[1], 0.0001f);
        assertTrue(insets[1] > insets[0]);
    }
}
