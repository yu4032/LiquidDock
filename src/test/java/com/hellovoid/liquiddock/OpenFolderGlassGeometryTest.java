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
