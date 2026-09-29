package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

/**
 * Launcher-side translation of the OS4 Launcher 8.0 Sidebar split effect.
 *
 * <p>Native evidence recovered from libapp_launcher.so:
 * GestureBackArrowView::build_teardrop_path @ 0x801044,
 * PathBackgroundProxy::draw_teardrop @ 0x7560c8,
 * AbstractSidebarSplitEffect::run_abstract_sidebar_anim_target @ 0x75af08, and
 * GestureBackArrowView::on_swipe_stop @ 0x7d607c.</p>
 *
 * <p>This renderer deliberately keeps Security Center as the final Sidebar owner. It only
 * reproduces the Launcher-owned confirmation/split presentation that OS3 is missing.</p>
 */
final class Launcher450Os4SidebarConfirmationRenderer {
    // run_abstract_sidebar_anim_target(show): 30dp fallback -> 24x53dp body, radius 8dp.
    private static final float START_WIDTH_DP = 30f;
    private static final float START_HEIGHT_DP = 30f;
    private static final float START_RADIUS_DP = 30f;
    private static final float TARGET_WIDTH_DP = 24f;
    private static final float TARGET_HEIGHT_DP = 53f;
    private static final float TARGET_RADIUS_DP = 8f;
    private static final float EDGE_SOURCE_OFFSET_DP = 12f;

    // on_swipe_stop @ 0x7d607c explicitly drives split_progress to -1.5f.
    private static final float RELEASE_SPLIT_TARGET = -1.5f;

    // Recovered hyper_folme target configs.
    private static final float WIDTH_DAMPING = 0.80f;
    private static final float WIDTH_RESPONSE_S = 0.58f;
    private static final float HEIGHT_DAMPING = 0.85f;
    private static final float HEIGHT_RESPONSE_S = 0.40f;
    private static final float RADIUS_DAMPING = 0.90f;
    private static final float RADIUS_RESPONSE_S = 0.68f;
    private static final float TEARDROP_DAMPING = 0.70f;
    private static final float TEARDROP_RESPONSE_S = 0.60f;
    private static final float BRIDGE_DAMPING = 0.85f;
    private static final float BRIDGE_RESPONSE_S = 0.55f;

    // The native show target is 1 for both properties. on_swipe_stop keeps the current values
    // unchanged while split_progress performs the release transition.
    private static final float TEARDROP_TARGET = 1f;
    private static final float BRIDGE_TARGET = 1f;

    private static final int PANEL_COLOR = 0xCC000000;
    private static final long MAX_FRAME_WINDOW_MS = 900L;

    private static final ThreadLocal<Paint> PAINT =
            ThreadLocal.withInitial(() -> new Paint(Paint.ANTI_ALIAS_FLAG));
    private static final ThreadLocal<Path> PATH =
            ThreadLocal.withInitial(Path::new);

    private Launcher450Os4SidebarConfirmationRenderer() {}

    static void draw(
            Canvas canvas,
            View arrowView,
            boolean leftEdge,
            float gestureRawY,
            long startedAtUptimeMs,
            long releaseStartedAtUptimeMs) {
        if (canvas == null || arrowView == null) return;

        float density = arrowView.getResources().getDisplayMetrics().density;
        long now = SystemClock.uptimeMillis();
        float elapsedSeconds = Math.max(0L, now - startedAtUptimeMs) / 1000f;
        boolean releasing = releaseStartedAtUptimeMs > 0L;

        float showWidthProgress =
                springProgress(elapsedSeconds, WIDTH_DAMPING, WIDTH_RESPONSE_S);
        float showHeightProgress =
                springProgress(elapsedSeconds, HEIGHT_DAMPING, HEIGHT_RESPONSE_S);
        float showRadiusProgress =
                springProgress(elapsedSeconds, RADIUS_DAMPING, RADIUS_RESPONSE_S);
        float teardropFactor =
                TEARDROP_TARGET
                        * springProgress(
                                elapsedSeconds, TEARDROP_DAMPING, TEARDROP_RESPONSE_S);
        float bridgeFactor =
                BRIDGE_TARGET
                        * springProgress(
                                elapsedSeconds, BRIDGE_DAMPING, BRIDGE_RESPONSE_S);

        float width;
        float height;
        float radius;
        float splitProgress;
        if (releasing) {
            width = TARGET_WIDTH_DP * density;
            height = TARGET_HEIGHT_DP * density;
            radius = TARGET_RADIUS_DP * density;

            float releaseSeconds =
                    Math.max(0L, now - releaseStartedAtUptimeMs) / 1000f;
            splitProgress =
                    lerp(
                            1f,
                            RELEASE_SPLIT_TARGET,
                            springProgress(
                                    releaseSeconds, WIDTH_DAMPING, WIDTH_RESPONSE_S));

            // OS4 on_swipe_stop adds current -> current targets for teardrop_factor and
            // bridge_factor. Do not collapse either shape during the Sidebar handoff.
            teardropFactor = TEARDROP_TARGET;
            bridgeFactor = BRIDGE_TARGET;
        } else {
            width =
                    lerp(
                            START_WIDTH_DP * density,
                            TARGET_WIDTH_DP * density,
                            showWidthProgress);
            height =
                    lerp(
                            START_HEIGHT_DP * density,
                            TARGET_HEIGHT_DP * density,
                            showHeightProgress);
            radius =
                    lerp(
                            START_RADIUS_DP * density,
                            TARGET_RADIUS_DP * density,
                            showRadiusProgress);
            splitProgress = showWidthProgress;
        }

        float viewWidth = arrowView.getWidth();
        float viewHeight = arrowView.getHeight();
        if (viewWidth <= 0f || viewHeight <= 0f) return;

        float sourceX =
                leftEdge
                        ? -EDGE_SOURCE_OFFSET_DP * density
                        : viewWidth + EDGE_SOURCE_OFFSET_DP * density;
        float targetX = leftEdge ? width * 0.5f : viewWidth - width * 0.5f;
        float centerX = lerp(sourceX, targetX, splitProgress);

        int[] location = new int[2];
        arrowView.getLocationOnScreen(location);
        float centerY = gestureRawY - location[1];
        float halfHeight = height * 0.5f;
        centerY = Math.max(halfHeight, Math.min(centerY, viewHeight - halfHeight));

        Paint paint = PAINT.get();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PANEL_COLOR);
        paint.setAlpha(255);

        // Native draw() renders the teardrop path and the split body's round-rect in the same
        // frame. Port that composition instead of replacing the whole effect with one rectangle.
        if (teardropFactor > 0.001f && bridgeFactor > 0.001f) {
            Path path = PATH.get();
            buildTeardropPath(
                    path,
                    leftEdge,
                    centerX,
                    centerY,
                    width,
                    height,
                    density,
                    teardropFactor,
                    bridgeFactor);
            canvas.drawPath(path, paint);
        }

        canvas.drawRoundRect(
                centerX - width * 0.5f,
                centerY - halfHeight,
                centerX + width * 0.5f,
                centerY + halfHeight,
                radius,
                radius,
                paint);

        if (releasing || now - startedAtUptimeMs < MAX_FRAME_WINDOW_MS) {
            arrowView.postInvalidateOnAnimation();
        }
    }

    /**
     * Structural translation of build_teardrop_path().
     *
     * <p>The native function builds a closed Path from the screen-edge baseline, a sampled
     * deformation profile, and cubic segments. The exact native sample table is not yet exported,
     * so this first port preserves the same authority and topology (edge baseline -> cubic bridge
     * -> body -> cubic bridge -> edge baseline) while keeping the recovered animated
     * teardrop_factor/bridge_factor separate. The sample table can be substituted later without
     * changing the gesture state machine.</p>
     */
    private static void buildTeardropPath(
            Path path,
            boolean leftEdge,
            float bodyCenterX,
            float centerY,
            float bodyWidth,
            float bodyHeight,
            float density,
            float teardropFactor,
            float bridgeFactor) {
        path.reset();

        float direction = leftEdge ? 1f : -1f;
        float edgeX = leftEdge ? 0f : bodyCenterX + bodyWidth * 0.5f;
        if (!leftEdge) {
            // The active edge is the ArrowView's right boundary. bodyCenterX can overshoot during
            // split release, so derive it from the body edge and the known body width.
            edgeX = bodyCenterX + bodyWidth * 0.5f;
        }

        float bodyNearX = bodyCenterX - direction * bodyWidth * 0.5f;
        float halfBody = bodyHeight * 0.5f;

        // The native profile displacement is normalized by 76.0 in build_teardrop_path.
        float maxBridge = 76f * density;
        float bridgeLength =
                Math.min(
                        maxBridge,
                        Math.max(0f, Math.abs(bodyNearX - edgeX)))
                        * clamp01(bridgeFactor);
        float neckX = edgeX + direction * bridgeLength;

        float neckHalf =
                lerp(
                        Math.max(2f * density, halfBody * 0.12f),
                        halfBody * 0.52f,
                        clamp01(teardropFactor));
        float shoulderHalf =
                lerp(
                        neckHalf,
                        halfBody * 0.82f,
                        clamp01(teardropFactor));

        float upperEdgeY = centerY - neckHalf;
        float lowerEdgeY = centerY + neckHalf;
        float upperBodyY = centerY - shoulderHalf;
        float lowerBodyY = centerY + shoulderHalf;

        path.moveTo(edgeX, upperEdgeY);
        path.cubicTo(
                edgeX + direction * bridgeLength * 0.34f,
                upperEdgeY,
                neckX - direction * bridgeLength * 0.20f,
                upperBodyY,
                bodyNearX,
                upperBodyY);
        path.lineTo(bodyNearX, lowerBodyY);
        path.cubicTo(
                neckX - direction * bridgeLength * 0.20f,
                lowerBodyY,
                edgeX + direction * bridgeLength * 0.34f,
                lowerEdgeY,
                edgeX,
                lowerEdgeY);
        path.close();
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
        return Math.max(0f, Math.min(value, 1.20f));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(value, 1f));
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }
}
