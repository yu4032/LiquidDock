package com.hellovoid.liquiddock;

/** Temporary, behavior-neutral diagnostics for Gboard floating-glass drag investigation. */
final class GboardDragDiagnostics {
    private static final String TAG = "[DC][GboardDragDiag]";
    private static final Object LOCK = new Object();

    private static float lastCaptureLeft = Float.NaN;
    private static float lastCaptureTop = Float.NaN;
    private static float lastCaptureSinkLeft = Float.NaN;
    private static float lastCaptureSinkTop = Float.NaN;
    private static float lastRenderLeft = Float.NaN;
    private static float lastRenderTop = Float.NaN;
    private static float lastCropU = Float.NaN;
    private static float lastCropV = Float.NaN;

    private GboardDragDiagnostics() {}

    static void log(String message) {
        try { Api101Bridge.log(TAG + " " + message); }
        catch (Throwable ignored) {}
    }

    static void log(String message, Throwable error) {
        try { Api101Bridge.log(TAG + " " + message, error); }
        catch (Throwable ignored) {}
    }

    static void geometryCaptured(GboardFloatingGlassGeometry geometry) {
        if (geometry == null) return;
        synchronized (LOCK) {
            if (same(lastCaptureLeft, geometry.left)
                    && same(lastCaptureTop, geometry.top)
                    && same(lastCaptureSinkLeft, geometry.sinkLeft)
                    && same(lastCaptureSinkTop, geometry.sinkTop)) return;
            lastCaptureLeft = geometry.left;
            lastCaptureTop = geometry.top;
            lastCaptureSinkLeft = geometry.sinkLeft;
            lastCaptureSinkTop = geometry.sinkTop;
        }
        log("GEOMETRY_CAPTURE root=" + geometry.rootWidth + "x" + geometry.rootHeight
                + " glass=" + f(geometry.left) + "," + f(geometry.top)
                + " " + f(geometry.width) + "x" + f(geometry.height)
                + " sink=" + f(geometry.sinkLeft) + "," + f(geometry.sinkTop)
                + " " + geometry.sinkWidthPx() + "x" + geometry.sinkHeightPx());
    }

    static void renderCrop(GboardFloatingGlassGeometry geometry, float[] crop) {
        if (geometry == null || crop == null || crop.length != 4) return;
        synchronized (LOCK) {
            if (same(lastRenderLeft, geometry.left)
                    && same(lastRenderTop, geometry.top)
                    && same(lastCropU, crop[0])
                    && same(lastCropV, crop[1])) return;
            lastRenderLeft = geometry.left;
            lastRenderTop = geometry.top;
            lastCropU = crop[0];
            lastCropV = crop[1];
        }
        log("RENDER_CROP glass=" + f(geometry.left) + "," + f(geometry.top)
                + " center=" + f(geometry.centerX) + "," + f(geometry.centerY)
                + " uv=" + f(crop[0]) + "," + f(crop[1])
                + " " + f(crop[2]) + "x" + f(crop[3]));
    }

    private static boolean same(float a, float b) {
        return !Float.isNaN(a) && Math.abs(a - b) < 0.0001f;
    }

    private static String f(float value) {
        return String.format(java.util.Locale.US, "%.3f", value);
    }
}
