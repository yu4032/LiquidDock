package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GboardSurfaceControlGeometryPolicyTest {
    @Test public void preservesIntegralRootGeometry() {
        GboardSurfaceControlGeometryPolicy.Frame frame =
                GboardSurfaceControlGeometryPolicy.from(372f, 421f, 1232, 978);
        assertEquals(372, frame.x);
        assertEquals(421, frame.y);
        assertEquals(1232, frame.width);
        assertEquals(978, frame.height);
    }

    @Test public void roundsFractionalPositionToNearestPixel() {
        GboardSurfaceControlGeometryPolicy.Frame frame =
                GboardSurfaceControlGeometryPolicy.from(372.49f, 421.51f, 1232, 978);
        assertEquals(372, frame.x);
        assertEquals(422, frame.y);
    }
}
