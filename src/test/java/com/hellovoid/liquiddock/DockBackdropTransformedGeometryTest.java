package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DockBackdropTransformedGeometryTest {
    @Test
    public void transformedBoundsPreserveScaleAndScreenOrigin() {
        DockBackdropTransformedGeometry.Bounds bounds =
                DockBackdropTransformedGeometry.fromQuad(
                        100f, 200f,
                        280f, 200f,
                        100f, 290f,
                        280f, 290f,
                        200, 100);

        assertEquals(100f, bounds.left, 0.0001f);
        assertEquals(200f, bounds.top, 0.0001f);
        assertEquals(180f, bounds.width, 0.0001f);
        assertEquals(90f, bounds.height, 0.0001f);
        assertEquals(0.9f, bounds.scaleX, 0.0001f);
        assertEquals(0.9f, bounds.scaleY, 0.0001f);
    }

    @Test
    public void sampleRectExpandsInsetsInTransformedScreenSpace() {
        DockBackdropTransformedGeometry.Bounds bounds =
                DockBackdropTransformedGeometry.fromQuad(
                        100f, 200f,
                        280f, 200f,
                        100f, 290f,
                        280f, 290f,
                        200, 100);

        DockBackdropTransformedGeometry.SampleRect sample =
                bounds.expandLocalInsets(20, 10, 30, 10);

        assertEquals(82f, sample.left, 0.0001f);
        assertEquals(173f, sample.top, 0.0001f);
        assertEquals(207f, sample.width, 0.0001f);
        assertEquals(126f, sample.height, 0.0001f);
    }

    @Test
    public void translationOnlyKeepsUnitScale() {
        DockBackdropTransformedGeometry.Bounds bounds =
                DockBackdropTransformedGeometry.fromQuad(
                        340f, 720f,
                        540f, 720f,
                        340f, 820f,
                        540f, 820f,
                        200, 100);

        assertEquals(1f, bounds.scaleX, 0.0001f);
        assertEquals(1f, bounds.scaleY, 0.0001f);
    }
}
