package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class WorkspaceFrameGeometryPolicyTest {
    @Test public void acceptsMatchedLogicalCaptureAndOutput() {
        assertNull(WorkspaceFrameGeometryPolicy.mismatch(
                3008, 1880, 0, 3008, 1880, 0, 3008, 1880));
    }

    @Test public void rejectsCaptureFromPreviousRootSize() {
        assertEquals("root-size", WorkspaceFrameGeometryPolicy.mismatch(
                3008, 1880, 0, 2800, 1880, 0, 2800, 1880));
    }

    @Test public void rejectsWindowResizeLagAndRotationLag() {
        assertEquals("window-size", WorkspaceFrameGeometryPolicy.mismatch(
                3008, 1880, 0, 3008, 1880, 0, 2400, 1880));
        assertEquals("rotation", WorkspaceFrameGeometryPolicy.mismatch(
                3008, 1880, 1, 3008, 1880, 0, 3008, 1880));
    }

    @Test public void allowsMissingStaticOutputWhileDragSceneUsesRootGeometry() {
        assertNull(WorkspaceFrameGeometryPolicy.mismatch(
                3008, 1880, 0, 3008, 1880, 0, 0, 0));
    }
}
