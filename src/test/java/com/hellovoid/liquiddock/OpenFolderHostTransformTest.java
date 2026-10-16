package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import org.junit.Test;

/** Typed regression for native view-matrix isolation, no production-source scanning. */
public class OpenFolderHostTransformTest {
    private static final float EPS = .0001f;

    private static OpenFolderHostTransform host(
            float x, float y, float sx, float sy) {
        return OpenFolderHostTransform.fromBasis(new float[] {
                x, y, x + sx, y, x, y + sy});
    }

    @Test public void parentScalingIsCancelledForTheFullscreenTransparentBuffer() {
        OpenFolderHostTransform nativeHost = host(80f, 45f, .92f, .92f);
        assertNotNull(nativeHost);
        float inv = nativeHost.inverseScaleX();
        float translation = nativeHost.inverseTranslationX(12);
        for (float logicalX : new float[]{0f, 150f, 720f, 1728f}) {
            float localInHost = 12f + inv * logicalX + translation;
            assertEquals(logicalX, nativeHost.x + nativeHost.scaleX * localInHost, .0002f);
        }
        float inverseY = nativeHost.inverseScaleY();
        float translationY = nativeHost.inverseTranslationY(18);
        for (float logicalY : new float[]{0f, 350f, 1080f}) {
            assertEquals(logicalY, nativeHost.y + nativeHost.scaleY
                    * (18f + inverseY * logicalY + translationY), .0002f);
        }
    }

    @Test public void onlyNativeGlassGeometryMovesWithTheHostSpring() {
        OpenFolderHostTransform baseline = host(0f, 0f, 1f, 1f);
        OpenFolderHostTransform current = host(36f, 55f, .92f, .92f);
        OpenFolderGlassGeometry.Frame expanded =
                new OpenFolderGlassGeometry.Frame(460f, 215f, 1076f, 806f, 46f);
        OpenFolderGlassGeometry.Frame nativeFrame = current.project(expanded, baseline);
        assertEquals(36f + 460f * .92f, nativeFrame.left, EPS);
        assertEquals(55f + 215f * .92f, nativeFrame.top, EPS);
        assertEquals(36f + 1076f * .92f, nativeFrame.right, EPS);
        assertEquals(55f + 806f * .92f, nativeFrame.bottom, EPS);
        assertEquals(46f * .92f, nativeFrame.radius, EPS);
        assertEquals(460f, expanded.left, EPS); // never mutate frozen optical UVs
        assertFalse(current.approximatelyEquals(baseline));
        assertTrue(current.approximatelyEquals(host(36f, 55f, .92f, .92f)));
    }

    @Test public void geometryIsProjectedRelativeToTheRealBaseline() {
        OpenFolderHostTransform baseline = host(10f, 20f, 1f, 1f);
        OpenFolderHostTransform current = host(40f, 80f, .9f, .9f);
        OpenFolderGlassGeometry.Frame rect =
                new OpenFolderGlassGeometry.Frame(100f, 200f, 500f, 600f, 30f);
        OpenFolderGlassGeometry.Frame projected = current.project(rect, baseline);
        assertEquals(40f + (100f - 10f) * .9f, projected.left, EPS);
        assertEquals(80f + (200f - 20f) * .9f, projected.top, EPS);
    }

    @Test public void unrepresentableOrNoninvertibleHostIsRejected() {
        assertNull(OpenFolderHostTransform.fromBasis(
                new float[] {0f, 0f, .92f, .2f, 0f, .92f}));
        assertNull(host(0f, 0f, .0001f, 1f));
        assertNull(host(0f, 0f, Float.NaN, 1f));
        assertNull(OpenFolderHostTransform.fromBasis(null));
    }
}
