package com.hellovoid.liquiddock;

import android.graphics.Path;
import android.graphics.RectF;

/**
 * View-outline geometry for the zero-copy Prismal host.
 *
 * Prismal's final SDF alpha mask uses the complete glass extent, so this path deliberately has no
 * half-pixel stroke inset. It is an outline/shadow boundary only and is never used to clip the
 * TextureView.
 */
final class DockPrismalOutlinePath {
    private DockPrismalOutlinePath() {}

    static void build(Path out, float width, float height, float radius,
                      boolean squircle, float cp) {
        out.reset();
        if (width <= 0f || height <= 0f) return;
        RectF bounds = new RectF(0f, 0f, width, height);
        float safeRadius = Math.max(0f,
                Math.min(radius, Math.min(bounds.width(), bounds.height()) * .5f));
        if (!squircle || safeRadius <= 1f) {
            out.addRoundRect(bounds, safeRadius, safeRadius, Path.Direction.CW);
            return;
        }

        float a = safeRadius;
        float c = a * Math.max(0.05f, Math.min(0.95f, cp));
        float l = bounds.left, t = bounds.top, r = bounds.right, b = bounds.bottom;
        out.moveTo(l, t + a);
        out.cubicTo(l, t + a - c, l + a - c, t, l + a, t);
        out.lineTo(r - a, t);
        out.cubicTo(r - a + c, t, r, t + a - c, r, t + a);
        out.lineTo(r, b - a);
        out.cubicTo(r, b - a + c, r - a + c, b, r - a, b);
        out.lineTo(l + a, b);
        out.cubicTo(l + a - c, b, l, b - a + c, l, b - a);
        out.close();
    }
}
