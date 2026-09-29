package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/**
 * Direct Java/Canvas transliteration of the OS4 Launcher 8.0 Sidebar confirmation body.
 *
 * <p>The vendor implementation lives in
 * recents/sidebar/abstract_sidebar_split_effect.rs and is rasterized from
 * GestureBackArrowView::on_vsync through RustCanvas_draw_round_rect. HyperOS 3 Launcher does not
 * ship libapp_launcher.so / hyper_folme / RustCanvas, so this class keeps the recovered geometry
 * and spring parameters while targeting the Canvas already owned by OS3 GestureBackArrowView.
 * It intentionally does not invent the inner Sidebar icon: its CachedIconDp constants are still
 * outlined in the native binary and are not evidence-complete yet.</p>
 */
final class Launcher450Os4SidebarConfirmationRenderer {
    // run_abstract_sidebar_anim_target(is_show=1):
    // current fallback = 30dp circle; show target = 24dp x 53dp, radius 8dp.
    private static final float START_WIDTH_DP = 30f;
    private static final float START_HEIGHT_DP = 30f;
    private static final float START_RADIUS_DP = 30f;
    private static final float TARGET_WIDTH_DP = 24f;
    private static final float TARGET_HEIGHT_DP = 53f;
    private static final float TARGET_RADIUS_DP = 8f;

    // Recovered AnimTarget configs:
    // width  {0.80, 0.58}, height {0.85, 0.40}, radius {0.90, 0.68}.
    // Treat the first value as damping ratio and the second as response seconds, matching the
    // hyper_folme spring parameter domain used by the native target.
    private static final float WIDTH_DAMPING = 0.80f;
    private static final float WIDTH_RESPONSE_S = 0.58f;
    private static final float HEIGHT_DAMPING = 0.85f;
    private static final float HEIGHT_RESPONSE_S = 0.40f;
    private static final float RADIUS_DAMPING = 0.90f;
    private static final float RADIUS_RESPONSE_S = 0.68f;

    private static final int PANEL_COLOR = 0xCC000000;
    // Stop requesting frames after the slowest recovered spring has visually converged.
    private static final long MAX_FRAME_WINDOW_MS = 900L;

    private static final ThreadLocal<Paint> PAINT =
            ThreadLocal.withInitial(() -> new Paint(Paint.ANTI_ALIAS_FLAG));

    private Launcher450Os4SidebarConfirmationRenderer() {}

    static void draw(
            Canvas canvas,
            View arrowView,
            boolean leftEdge,
            float gestureRawY,
            long startedAtUptimeMs) {
        if (canvas == null || arrowView == null) return;

        float density = arrowView.getResources().getDisplayMetrics().density;
        float elapsedSeconds =
                Math.max(0L, SystemClock.uptimeMillis() - startedAtUptimeMs) / 1000f;

        float width = lerp(
                START_WIDTH_DP * density,
                TARGET_WIDTH_DP * density,
                springProgress(elapsedSeconds, WIDTH_DAMPING, WIDTH_RESPONSE_S));
        float height = lerp(
                START_HEIGHT_DP * density,
                TARGET_HEIGHT_DP * density,
                springProgress(elapsedSeconds, HEIGHT_DAMPING, HEIGHT_RESPONSE_S));
        float radius = lerp(
                START_RADIUS_DP * density,
                TARGET_RADIUS_DP * density,
                springProgress(elapsedSeconds, RADIUS_DAMPING, RADIUS_RESPONSE_S));

        float viewWidth = arrowView.getWidth();
        float viewHeight = arrowView.getHeight();
        if (viewWidth <= 0f || viewHeight <= 0f) return;

        // OS4's confirmation body is edge-attached. Keeping the body half-width inside the edge
        // reproduces the native endpoint while avoiding the old detached/synthetic preview.
        float centerX = leftEdge ? width * 0.5f : viewWidth - width * 0.5f;

        int[] location = new int[2];
        arrowView.getLocationOnScreen(location);
        float centerY = gestureRawY - location[1];
        float halfHeight = height * 0.5f;
        centerY = Math.max(halfHeight, Math.min(centerY, viewHeight - halfHeight));

        Paint paint = PAINT.get();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PANEL_COLOR);
        paint.setAlpha(255);

        canvas.drawRoundRect(
                centerX - width * 0.5f,
                centerY - halfHeight,
                centerX + width * 0.5f,
                centerY + halfHeight,
                radius,
                radius,
                paint);

        if (SystemClock.uptimeMillis() - startedAtUptimeMs < MAX_FRAME_WINDOW_MS) {
            arrowView.postInvalidateOnAnimation();
        }
    }

    private static float springProgress(float t, float damping, float responseSeconds) {
        if (t <= 0f) return 0f;
        float omega0 = (float) (2.0 * Math.PI / responseSeconds);
        float oneMinusZetaSq = Math.max(0.0001f, 1f - damping * damping);
        float omegaD = omega0 * (float) Math.sqrt(oneMinusZetaSq);
        float envelope = (float) Math.exp(-damping * omega0 * t);
        float phase =
                (float) Math.cos(omegaD * t)
                        + (damping / (float) Math.sqrt(oneMinusZetaSq))
                        * (float) Math.sin(omegaD * t);
        float value = 1f - envelope * phase;
        // Geometry must stay valid even if the native-style spring overshoots.
        return Math.max(0f, Math.min(value, 1.20f));
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }
}
