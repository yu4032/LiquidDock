package com.hellovoid.liquiddock;

import android.graphics.Path;
import android.graphics.RectF;
import com.hellovoid.prismal.PrismalContinuousCorners;

/** One icon silhouette for static TextView artwork and moving icon proxies. */
final class LauncherIconGlassShapePath {
    private LauncherIconGlassShapePath() {}

    static void build(Path out, float left, float top, float right, float bottom,
                      float radius, boolean continuous) {
        out.reset();
        if (!(right > left && bottom > top)) return;
        if (continuous) {
            float[] points = PrismalContinuousCorners.contour(
                    right - left, bottom - top, radius);
            if (points.length >= 6 && (points.length & 1) == 0) {
                out.moveTo(left + points[0], top + points[1]);
                for (int i = 2; i < points.length; i += 2) {
                    out.lineTo(left + points[i], top + points[i + 1]);
                }
                out.close();
                return;
            }
        }
        out.addRoundRect(new RectF(left, top, right, bottom), radius, radius,
                Path.Direction.CW);
    }
}
