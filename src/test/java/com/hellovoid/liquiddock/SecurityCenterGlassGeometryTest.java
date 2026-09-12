package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.hellovoid.prismal.PrismalGeometry;

import java.lang.reflect.Method;

import org.junit.Test;

/** Pure root-local geometry contract for Security Center Global Dock and All Apps. */
public class SecurityCenterGlassGeometryTest {
    @Test
    public void leftAndRightSidebarScreenBoundsMapIntoRootLocalCoordinates() {
        SecurityCenterGlassGeometry left = SecurityCenterGlassGeometry.resolve(
                1200, 2000,
                40f, 80f,
                40f, 120f, 360f, 1880f,
                32f);
        assertNotNull(left);
        assertEquals(0f, left.left, 0.001f);
        assertEquals(40f, left.top, 0.001f);
        assertEquals(320f, left.width, 0.001f);
        assertEquals(1760f, left.height, 0.001f);

        SecurityCenterGlassGeometry right = SecurityCenterGlassGeometry.resolve(
                1200, 2000,
                40f, 80f,
                920f, 120f, 1240f, 1880f,
                32f);
        assertNotNull(right);
        assertEquals(880f, right.left, 0.001f);
        assertEquals(40f, right.top, 0.001f);
        assertEquals(320f, right.width, 0.001f);
        assertEquals(1760f, right.height, 0.001f);
    }

    @Test
    public void allAppsBoundsRemainRootLocalAndProduceMatchingPrismalGeometry() {
        SecurityCenterGlassGeometry geometry = SecurityCenterGlassGeometry.resolve(
                1200, 1800,
                100f, 200f,
                180f, 260f, 1100f, 1780f,
                48f);
        assertNotNull(geometry);
        assertEquals(80f, geometry.left, 0.001f);
        assertEquals(60f, geometry.top, 0.001f);
        assertEquals(920f, geometry.width, 0.001f);
        assertEquals(1520f, geometry.height, 0.001f);

        PrismalGeometry prismal = geometry.toPrismalGeometry();
        assertEquals(1200, prismal.framebufferWidth);
        assertEquals(1800, prismal.framebufferHeight);
        assertEquals(540f, prismal.centerX, 0.001f);
        assertEquals(820f, prismal.centerY, 0.001f);
        assertEquals(920f, prismal.glassWidth, 0.001f);
        assertEquals(1520f, prismal.glassHeight, 0.001f);
    }

    @Test
    public void presentationCropUsesTargetRegionInsteadOfFullRoot() throws Exception {
        SecurityCenterGlassGeometry geometry = SecurityCenterGlassGeometry.resolve(
                1200, 1800,
                100f, 200f,
                180f, 260f, 1100f, 1780f,
                48f);
        assertNotNull(geometry);

        Method method = SecurityCenterGlassGeometry.class.getDeclaredMethod("toCropUvRect");
        method.setAccessible(true);
        float[] crop = (float[]) method.invoke(geometry);

        assertEquals(80f / 1200f, crop[0], 0.0001f);
        assertEquals(220f / 1800f, crop[1], 0.0001f);
        assertEquals(920f / 1200f, crop[2], 0.0001f);
        assertEquals(1520f / 1800f, crop[3], 0.0001f);
    }

    @Test
    public void presentationPlacementUsesRealTargetAndParentScreenOrigins() throws Exception {
        SecurityCenterGlassGeometry geometry = SecurityCenterGlassGeometry.resolve(
                1200, 1800,
                100f, 200f,
                180f, 260f, 1100f, 1780f,
                48f);
        assertNotNull(geometry);

        Method method = SecurityCenterGlassGeometry.class.getDeclaredMethod(
                "toParentPlacement", float.class, float.class, float.class, float.class);
        method.setAccessible(true);
        float[] placement = (float[]) method.invoke(geometry, 100f, 200f, 40f, 50f);

        assertEquals(140f, placement[0], 0.001f);
        assertEquals(210f, placement[1], 0.001f);
        assertEquals(920f, placement[2], 0.001f);
        assertEquals(1520f, placement[3], 0.001f);
    }

    @Test
    public void geometryChangesNeverAdvanceSceneContentGeneration() {
        SecurityCenterGlassSceneState scene = new SecurityCenterGlassSceneState();
        scene.onRootAttached();
        long generation = scene.generation();

        SecurityCenterGlassGeometry first = SecurityCenterGlassGeometry.resolve(
                1000, 1600, 0f, 0f, 0f, 0f, 320f, 1600f, 24f);
        SecurityCenterGlassGeometry second = SecurityCenterGlassGeometry.resolve(
                1000, 1600, 0f, 0f, 680f, 0f, 1000f, 1600f, 24f);
        assertNotNull(first);
        assertNotNull(second);
        assertFalse(first.sameAs(second));
        assertEquals(generation, scene.generation());
    }
}
