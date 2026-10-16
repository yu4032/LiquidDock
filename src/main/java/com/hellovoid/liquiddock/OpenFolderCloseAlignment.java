package com.hellovoid.liquiddock;

/** Root-space bridge between the opened Folder background and OEM animation
 * ImageView. The two Views have different layout origins even when their
 * rendered plate is the same object. Calibrate ONCE at native close handoff
 * and retain that translation while using the animated image's live bounds.
 */
final class OpenFolderCloseAlignment {
    private float dx, dy;
    private boolean calibrated;

    boolean isCalibrated() { return calibrated; }
    float deltaX() { return dx; }
    float deltaY() { return dy; }

    void calibrate(OpenFolderGlassGeometry.Frame panel, OpenFolderGlassGeometry.Frame image) {
        if (!valid(panel) || !valid(image)) return;
        dx = ((panel.left + panel.right) - (image.left + image.right)) * .5f;
        dy = ((panel.top + panel.bottom) - (image.top + image.bottom)) * .5f;
        calibrated = true;
    }

    OpenFolderGlassGeometry.Frame apply(OpenFolderGlassGeometry.Frame image) {
        if (!calibrated || !valid(image)) return image;
        return new OpenFolderGlassGeometry.Frame(
                image.left + dx, image.top + dy,
                image.right + dx, image.bottom + dy, image.radius);
    }

    void reset() { calibrated = false; dx = dy = 0f; }

    private static boolean valid(OpenFolderGlassGeometry.Frame f) {
        return f != null && Float.isFinite(f.left) && Float.isFinite(f.top)
                && Float.isFinite(f.right) && Float.isFinite(f.bottom)
                && f.right > f.left && f.bottom > f.top;
    }
}
