package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class OpenFolderGlassGeometryTest {
    @Test public void nativeProgressExpandsIntoTheRealPanelWithoutAnEndpointSwitch() throws Exception {
        Object geometry = geometry();
        Object closed = frame(100, 200, 180, 280, 24);
        Object panel = frame(40, 100, 640, 800, 70);
        assertRect(progress(geometry, closed, panel, 0f), 100, 200, 180, 280, 24);
        assertRect(progress(geometry, closed, panel, .5f), 70, 150, 410, 540, 47);
        Object last = progress(geometry, closed, panel, 1f);
        assertRect(last, 40, 100, 640, 800, 70);
        assertRect(accept(geometry, "acceptPanel", panel, 1000, 1200), 40, 100, 640, 800, 70);
        assertRect(progress(geometry, closed, panel, .5f), 70, 150, 410, 540, 47);
        assertSame(geometry.getClass().getDeclaredMethod("current").invoke(geometry),
                progress(geometry, closed, panel, Float.NaN));
    }

    private static Object progress(Object geometry, Object closed, Object panel, float progress) throws Exception {
        return type().getDeclaredMethod("acceptProgress", frameType(), frameType(), float.class, int.class, int.class)
                .invoke(geometry, closed, panel, progress, 1000, 1200);
    }
    @Test public void expansionAndContractionFollowNativeBoundsWithoutAnotherAnimation() throws Exception {
        Object geometry = geometry();
        Object small = accept(geometry, "acceptAnimation", frame(100, 200, 180, 280, 24), 1000, 1200);
        assertRect(small, 100, 200, 180, 280, 24);
        Object expanded = accept(geometry, "acceptAnimation", frame(40, 100, 640, 800, 400), 1000, 1200);
        assertRect(expanded, 40, 100, 640, 800, 300);
        Object panel = accept(geometry, "acceptPanel", frame(40, 100, 640, 800, 70), 1000, 1200);
        assertRect(panel, 40, 100, 640, 800, 70);
        assertRect(accept(geometry, "acceptAnimation", frame(100, 200, 180, 280, 24), 1000, 1200),
                100, 200, 180, 280, 24);
    }

    @Test public void missingOrInvalidObservationCannotReplaceLastValidHandoffFrame() throws Exception {
        Object geometry = geometry();
        Object valid = accept(geometry, "acceptAnimation", frame(100, 200, 180, 280, 24), 1000, 1200);
        assertSame(valid, accept(geometry, "acceptPanel", null, 1000, 1200));
        assertSame(valid, accept(geometry, "acceptPanel", frame(Float.NaN, 0, 10, 20, 3), 1000, 1200));
        assertSame(valid, accept(geometry, "acceptPanel", frame(10, 20, 10, 20, 3), 1000, 1200));
        geometry.getClass().getDeclaredMethod("reset").invoke(geometry);
        assertNull(geometry.getClass().getDeclaredMethod("current").invoke(geometry));
    }

    @Test public void changedOrInvalidViewportCannotReuseTheOldFrame() throws Exception {
        Object geometry = geometry();
        accept(geometry, "acceptAnimation", frame(100, 200, 180, 280, 24), 1000, 1200);
        assertNull(accept(geometry, "acceptPanel", null, 1200, 1000));
        assertNull(accept(geometry, "acceptAnimation", frame(100, 200, 180, 280, 24), 0, 1000));
        assertRect(accept(geometry, "acceptPanel", frame(20, 30, 400, 500, 48), 1200, 1000),
                20, 30, 400, 500, 48);
    }

    @Test public void recentsBackdropContinuouslyContractsFullscreenIntoSmallFolderRoundRect() {
        // Representative expanded 1x1 native folder panel, NOT a desktop icon.
        OpenFolderGlassGeometry.Frame folder =
                new OpenFolderGlassGeometry.Frame(460f, 215f, 1076f, 806f, 46f);
        OpenFolderGlassGeometry.Frame full =
                OpenFolderGlassGeometry.recentsBackdropMask(folder, 1728, 1080, 0f);
        assertEquals(0f, full.left, .001f);
        assertEquals(0f, full.top, .001f);
        assertEquals(1728f, full.right, .001f);
        assertEquals(1080f, full.bottom, .001f);
        assertEquals(0f, full.radius, .001f);

        OpenFolderGlassGeometry.Frame half =
                OpenFolderGlassGeometry.recentsBackdropMask(folder, 1728, 1080, .5f);
        assertEquals(230f, half.left, .001f);
        assertEquals(107.5f, half.top, .001f);
        assertEquals(1402f, half.right, .001f);
        assertEquals(943f, half.bottom, .001f);
        assertEquals(23f, half.radius, .001f);

        OpenFolderGlassGeometry.Frame end =
                OpenFolderGlassGeometry.recentsBackdropMask(folder, 1728, 1080, 1f);
        assertEquals(folder.left, end.left, .001f);
        assertEquals(folder.top, end.top, .001f);
        assertEquals(folder.right, end.right, .001f);
        assertEquals(folder.bottom, end.bottom, .001f);
        assertEquals(folder.radius, end.radius, .001f);
    }

    @Test public void recentsBackdropUsesActualExpandedPanelForLargeFolderAndClampsProgress() {
        OpenFolderGlassGeometry.Frame large =
                new OpenFolderGlassGeometry.Frame(150f, 90f, 1550f, 990f, 90f);
        OpenFolderGlassGeometry.Frame end =
                OpenFolderGlassGeometry.recentsBackdropMask(large, 1728, 1080, 2f);
        assertEquals(large.left, end.left, .001f);
        assertEquals(large.right, end.right, .001f);
        assertEquals(large.radius, end.radius, .001f);
        OpenFolderGlassGeometry.Frame start =
                OpenFolderGlassGeometry.recentsBackdropMask(large, 1728, 1080, -1f);
        assertEquals(0f, start.radius, .001f);
        assertEquals(1728f, start.right, .001f);
        assertEquals(1080f, start.bottom, .001f);
        assertNull(OpenFolderGlassGeometry.recentsBackdropMask(null, 1728, 1080, .4f));
        assertNull(OpenFolderGlassGeometry.recentsBackdropMask(large, 0, 1080, .4f));
        // Missing native progress fails to the existing safe folder mask,
        // never a scaled duplicate of the full-screen wallpaper.
        OpenFolderGlassGeometry.Frame missing =
                OpenFolderGlassGeometry.recentsBackdropMask(large, 1728, 1080, Float.NaN);
        assertEquals(large.left, missing.left, .001f);
        assertEquals(large.radius, missing.radius, .001f);
    }

    @Test public void nativeCloseBackdropContractsToDesktopSmallFolderIconOnActualSpringProgress() {
        // Native expanded folder is much larger; the CLOSE target is the
        // desktop 1x1 folder *icon*, not the Recents expanded panel.
        OpenFolderGlassGeometry.Frame icon =
                new OpenFolderGlassGeometry.Frame(780f, 450f, 940f, 610f, 34f);
        OpenFolderGlassGeometry.Frame start =
                OpenFolderGlassGeometry.closingBackdropMask(icon, 1728, 1080, 1f);
        assertEquals(0f, start.left, .001f);
        assertEquals(0f, start.top, .001f);
        assertEquals(1728f, start.right, .001f);
        assertEquals(1080f, start.bottom, .001f);
        assertEquals(0f, start.radius, .001f);
        OpenFolderGlassGeometry.Frame midway =
                OpenFolderGlassGeometry.closingBackdropMask(icon, 1728, 1080, .5f);
        assertEquals(390f, midway.left, .001f);
        assertEquals(225f, midway.top, .001f);
        assertEquals(1334f, midway.right, .001f);
        assertEquals(845f, midway.bottom, .001f);
        assertEquals(17f, midway.radius, .001f);
        OpenFolderGlassGeometry.Frame end =
                OpenFolderGlassGeometry.closingBackdropMask(icon, 1728, 1080, 0f);
        assertEquals(icon.left, end.left, .001f);
        assertEquals(icon.top, end.top, .001f);
        assertEquals(icon.right, end.right, .001f);
        assertEquals(icon.bottom, end.bottom, .001f);
        assertEquals(icon.radius, end.radius, .001f);
    }

    @Test public void nativeCloseBackdropHasNoFixedSizeAndClampsSpringOvershoot() {
        OpenFolderGlassGeometry.Frame largeIcon =
                new OpenFolderGlassGeometry.Frame(200f, 250f, 540f, 590f, 70f);
        OpenFolderGlassGeometry.Frame finished =
                OpenFolderGlassGeometry.closingBackdropMask(largeIcon, 1728, 1080, -0.1f);
        assertEquals(largeIcon.right, finished.right, .001f);
        assertEquals(largeIcon.radius, finished.radius, .001f);
        OpenFolderGlassGeometry.Frame started =
                OpenFolderGlassGeometry.closingBackdropMask(largeIcon, 1728, 1080, 1.1f);
        assertEquals(0f, started.radius, .001f);
        assertEquals(1728f, started.right, .001f);
        // A missing native progress sample retains the full backdrop, not an
        // arbitrary half-contracted mask that flashes during setup.
        OpenFolderGlassGeometry.Frame missing =
                OpenFolderGlassGeometry.closingBackdropMask(largeIcon, 1728, 1080, Float.NaN);
        assertEquals(1728f, missing.right, .001f);
        assertNull(OpenFolderGlassGeometry.closingBackdropMask(null, 1728, 1080, .4f));
        assertNull(OpenFolderGlassGeometry.closingBackdropMask(largeIcon, 0, 1080, .4f));
    }

    private static Class<?> type() {
        try { return Class.forName("com.hellovoid.liquiddock.OpenFolderGlassGeometry"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Missing native folder geometry policy", missing); }
    }
    private static Object geometry() throws Exception { return type().getDeclaredConstructor().newInstance(); }
    private static Class<?> frameType() throws Exception { return Class.forName(type().getName() + "$Frame"); }
    private static Object frame(float l, float t, float r, float b, float radius) throws Exception {
        return frameType().getDeclaredConstructor(float.class, float.class, float.class, float.class, float.class)
                .newInstance(l, t, r, b, radius);
    }
    private static Object accept(Object geometry, String name, Object frame, int w, int h) throws Exception {
        return type().getDeclaredMethod(name, frameType(), int.class, int.class).invoke(geometry, frame, w, h);
    }
    private static void assertRect(Object frame, float l, float t, float r, float b, float radius) throws Exception {
        assertNotNull(frame);
        String[] fields = {"left", "top", "right", "bottom", "radius"};
        float[] values = {l, t, r, b, radius};
        for (int i = 0; i < fields.length; i++) assertEquals(fields[i], values[i],
                frame.getClass().getDeclaredField(fields[i]).getFloat(frame), 0.001f);
    }
}
