package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class WorkstationNativeTaskbarGeometryTest {
    @Test public void expandsTheNativeBodyThroughItsCurrentParentScale() {
        assertEquals(2f, WorkstationNativeTaskbarGeometry.scaleX(1f, 500, 500f, 1000), .0001f);
        assertEquals(4f, WorkstationNativeTaskbarGeometry.scaleX(1f, 500, 250f, 1000), .0001f);
        assertEquals(2f, WorkstationNativeTaskbarGeometry.scaleX(2f, 500, 1000f, 1000), .0001f);
    }

    @Test public void centersTheExpandedBodyWithoutAccumulatingTranslation() {
        assertEquals(-100f, WorkstationNativeTaskbarGeometry.translationX(0f, 100f, 1f), .0001f);
        assertEquals(-100f, WorkstationNativeTaskbarGeometry.translationX(-100f, 0f, 1f), .0001f);
        assertEquals(-200f, WorkstationNativeTaskbarGeometry.translationX(0f, 100f, .5f), .0001f);
    }

    @Test public void incompleteOrDegenerateGeometryPreservesNativeTransform() {
        assertEquals(1.2f, WorkstationNativeTaskbarGeometry.scaleX(1.2f, 0, 0f, 1000), 0f);
        assertEquals(12f, WorkstationNativeTaskbarGeometry.translationX(12f, 100f, 0f), 0f);
    }
}
