package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

/**
 * Source-level port of the OS4 Launcher 8.0 GestureBackArrowView Sidebar background/split morph.
 *
 * <p>The path kernel is translated from GestureBackArrowView::build_teardrop_path (0x801044).
 * The source control points and Catmull-Rom expansion are translated from
 * GestureBackArrowView::create_background_proxy (0x7d7c88). The two-stage water-drop -> miniature
 * Sidebar morph follows AbstractSidebarSplitEffect::run_abstract_sidebar_anim_target (0x75af08).
 * PathBackgroundProxy::draw_teardrop (0x7560c8) supplies the original fill + border paint pair.</p>
 */
final class Launcher450Os4SidebarConfirmationRenderer {
    private static final float CIRCLE_WIDTH_DP = 30f;
    private static final float CIRCLE_HEIGHT_DP = 30f;
    private static final float CIRCLE_RADIUS_DP = 30f;
    private static final float SIDEBAR_WIDTH_DP = 24f;
    private static final float SIDEBAR_HEIGHT_DP = 53f;
    private static final float SIDEBAR_RADIUS_DP = 8f;
    private static final float FALLBACK_EDGE_INSET_DP = 12f;

    // Exact Folme configs recovered from run_abstract_sidebar_anim_target.
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
    private static final float SPLIT_DAMPING = 0.90f;
    private static final float SPLIT_RESPONSE_S = 0.68f;

    // OS4 split_effect_renderer enters the split branch at gesture progress 0.8 and reaches the
    // full split target at 1.0. These values are recovered as DAT_0026d2c4=0.8 and
    // DAT_0026e09c=0.2.
    private static final float SPLIT_START_PROGRESS = 0.8f;
    private static final float SPLIT_RANGE = 0.2f;

    // Native bridge-path constants from 0x9bdea8.
    private static final float BRIDGE_PROFILE_BASE = 0.2f;
    private static final float BRIDGE_PROFILE_SPAN = 0.1f;
    private static final float BRIDGE_CONTROL = 0.7f;
    private static final float BRIDGE_HALF_WIDTH_START_DP = 8f;
    private static final float BRIDGE_HALF_WIDTH_END_DP = 1.5f;

    // build_teardrop_path native constants.
    private static final float PROFILE_HEIGHT = 775f;
    private static final float PROFILE_WIDTH = 76f;
    private static final float CUBIC_DISTANCE_NORMALIZER = 20f;
    private static final float TANGENT_EDGE = 0.08f;
    private static final float TANGENT_NORMAL = 0.15f;
    private static final float TANGENT_MIDDLE = 0.25f;

    // Exact source table recovered from create_background_proxy.
    private static final float[][] OS4_SOURCE_PROFILE = {
            {0f, 0f},
            {50f, 2f},
            {100f, 5f},
            {150f, 12f},
            {200f, 24f},
            {250f, 41f},
            {300f, 60f},
            {350f, 73f},
            {400f, 76f},
            {450f, 70f},
            {500f, 54f},
            {550f, 35f},
            {600f, 20f},
            {650f, 10f},
            {700f, 4f},
            {750f, 1f},
            {775f, 0f}
    };

    private static final float[][] OS4_PROFILE = buildNativeProfile();

    private static final int BACKGROUND_COLOR = 0xCC000000;
    private static final int BORDER_COLOR = 0x20000000;
    private static final long MAX_FRAME_WINDOW_MS = 900L;

    private static final ThreadLocal<Paint> FILL_PAINT =
            ThreadLocal.withInitial(() -> {
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(BACKGROUND_COLOR);
                return paint;
            });
    private static final ThreadLocal<Paint> BORDER_PAINT =
            ThreadLocal.withInitial(() -> {
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1f);
                paint.setColor(BORDER_COLOR);
                return paint;
            });
    private static final ThreadLocal<Path> PATH =
            ThreadLocal.withInitial(Path::new);

    private Launcher450Os4SidebarConfirmationRenderer() {}

    static void draw(
            Canvas canvas,
            View arrowView,
            boolean leftEdge,
            float arrowLocalCenterY,
            float arrowStartX,
            float arrowExpectedHeight,
            float gestureProgress,
            boolean splitActive,
            long splitStartedAtUptimeMs,
            float gestureRawY,
            long startedAtUptimeMs,
            long releaseStartedAtUptimeMs) {
        if (canvas == null || arrowView == null) return;

        float density = arrowView.getResources().getDisplayMetrics().density;
        float viewWidth = arrowView.getWidth();
        float viewHeight = arrowView.getHeight();
        if (viewWidth <= 0f || viewHeight <= 0f) return;

        long now = SystemClock.uptimeMillis();
        boolean releasing = releaseStartedAtUptimeMs > 0L;

        float width;
        float height;
        float radius;
        float teardropFactor;
        float bridgeFactor;
        float splitProgress;

        float sourceProgress = clamp01(gestureProgress);
        boolean splitGateReached =
                splitActive && sourceProgress >= SPLIT_START_PROGRESS;

        // OS4 uses the 0.8 gesture threshold as the gate that starts a show=true Folme target.
        // After the gate is crossed, split_progress itself animates to 1 independently of any
        // further pointer distance.
        float splitSeconds = splitGateReached && splitStartedAtUptimeMs > 0L
                ? Math.max(0L, now - splitStartedAtUptimeMs) / 1000f
                : 0f;

        splitProgress = splitGateReached
                ? clamp01(springProgress(splitSeconds, SPLIT_DAMPING, SPLIT_RESPONSE_S))
                : 0f;

        float widthProgress = splitGateReached
                ? clamp01(springProgress(splitSeconds, WIDTH_DAMPING, WIDTH_RESPONSE_S))
                : 0f;
        float heightProgress = splitGateReached
                ? clamp01(springProgress(splitSeconds, HEIGHT_DAMPING, HEIGHT_RESPONSE_S))
                : 0f;
        float radiusProgress = splitGateReached
                ? clamp01(springProgress(splitSeconds, RADIUS_DAMPING, RADIUS_RESPONSE_S))
                : 0f;

        width = lerp(CIRCLE_WIDTH_DP * density, SIDEBAR_WIDTH_DP * density, widthProgress);
        height = lerp(CIRCLE_HEIGHT_DP * density, SIDEBAR_HEIGHT_DP * density, heightProgress);
        radius = lerp(CIRCLE_RADIUS_DP * density, SIDEBAR_RADIUS_DP * density, radiusProgress);

        float teardropCollapse = splitGateReached
                ? clamp01(springProgress(splitSeconds, TEARDROP_DAMPING, TEARDROP_RESPONSE_S))
                : 0f;
        float bridgeCollapse = splitGateReached
                ? clamp01(springProgress(splitSeconds, BRIDGE_DAMPING, BRIDGE_RESPONSE_S))
                : 0f;
        teardropFactor = 1f - teardropCollapse;
        bridgeFactor = 1f - bridgeCollapse;

        if (releasing) {
            // By ACTION_UP the mini Sidebar already exists. Release only removes any residual
            // water-drop/bridge while keeping the body at its split endpoint for vendor handoff.
            float releaseSeconds =
                    Math.max(0L, now - releaseStartedAtUptimeMs) / 1000f;
            float releaseDrop =
                    clamp01(springProgress(releaseSeconds, TEARDROP_DAMPING, 0.10f));
            teardropFactor *= 1f - releaseDrop;
            bridgeFactor *= 1f - releaseDrop;

            float releaseSplit =
                    clamp01(springProgress(releaseSeconds, SPLIT_DAMPING, SPLIT_RESPONSE_S));
            splitProgress = Math.max(splitProgress, releaseSplit);
            width = lerp(width, SIDEBAR_WIDTH_DP * density, releaseSplit);
            height = lerp(height, SIDEBAR_HEIGHT_DP * density, releaseSplit);
            radius = lerp(radius, SIDEBAR_RADIUS_DP * density, releaseSplit);
        }

        // OS3 already computes the authoritative local geometry in onActionDown(y,startX,height).
        // Reuse it instead of rebuilding coordinates from MotionEvent/raw-screen values.
        float baselineX;
        if (!Float.isNaN(arrowStartX)) {
            baselineX = leftEdge ? arrowStartX : viewWidth - arrowStartX;
        } else {
            baselineX = leftEdge
                    ? FALLBACK_EDGE_INSET_DP * density
                    : viewWidth - FALLBACK_EDGE_INSET_DP * density;
        }

        int[] location = new int[2];
        arrowView.getLocationOnScreen(location);
        float centerY = !Float.isNaN(arrowLocalCenterY)
                ? arrowLocalCenterY
                : gestureRawY - location[1];

        float nativeHalfHeight =
                !Float.isNaN(arrowExpectedHeight) && arrowExpectedHeight > 0f
                        ? arrowExpectedHeight * 0.5f
                        : 0f;
        if (nativeHalfHeight > 0f) {
            centerY = Math.max(nativeHalfHeight, Math.min(centerY, viewHeight - nativeHalfHeight));
        }

        float halfWidth = width * 0.5f;
        float halfHeight = height * 0.5f;
        float centerX = leftEdge
                ? baselineX + halfWidth
                : baselineX - halfWidth;

        // The water-drop path shares the same baseline as OS3's stock Back background.
        if (teardropFactor > 0.001f && bridgeFactor > 0.001f) {
            float profileLeft = leftEdge ? baselineX : centerX - halfWidth;
            float profileRight = leftEdge ? centerX + halfWidth : baselineX;
            if (profileRight - profileLeft > 0.001f) {
                Path path = PATH.get();
                buildNativeTeardropPath(
                        path,
                        profileLeft,
                        profileRight,
                        centerY,
                        height,
                        !leftEdge,
                        1f,
                        teardropFactor);
                canvas.drawPath(path, FILL_PAINT.get());
                canvas.drawPath(path, BORDER_PAINT.get());
            }
        }

        if (splitProgress > 0.001f) {
            // 0x9bdea8 draws an additional closed bridge/neck path between the teardrop and the
            // mini Sidebar before drawing the body. Keep it as a separate layer; otherwise the
            // animation collapses visually into a single ball.
            drawNativeSplitBridge(
                    canvas,
                    leftEdge,
                    baselineX,
                    centerX,
                    centerY,
                    width,
                    height,
                    splitProgress,
                    teardropFactor,
                    density,
                    FILL_PAINT.get(),
                    BORDER_PAINT.get());

            // The mini Sidebar is Launcher-owned and appears during the held gesture, immediately
            // after the water-drop enters the native split state. It is already present before
            // ACTION_UP; release only removes the remaining teardrop and hands this body to SC.
            float reveal = smoothStep(0f, 0.20f, splitProgress);
            Paint fill = FILL_PAINT.get();
            Paint border = BORDER_PAINT.get();
            int oldFillAlpha = fill.getAlpha();
            int oldBorderAlpha = border.getAlpha();
            fill.setAlpha(Math.round(oldFillAlpha * reveal));
            border.setAlpha(Math.round(oldBorderAlpha * reveal));
            canvas.drawRoundRect(
                    centerX - halfWidth,
                    centerY - halfHeight,
                    centerX + halfWidth,
                    centerY + halfHeight,
                    radius,
                    radius,
                    fill);
            canvas.drawRoundRect(
                    centerX - halfWidth,
                    centerY - halfHeight,
                    centerX + halfWidth,
                    centerY + halfHeight,
                    radius,
                    radius,
                    border);
            fill.setAlpha(oldFillAlpha);
            border.setAlpha(oldBorderAlpha);
        }

        if (releasing
                || now - startedAtUptimeMs < MAX_FRAME_WINDOW_MS
                || splitProgress < 0.999f
                || teardropFactor > 0.001f) {
            arrowView.postInvalidateOnAnimation();
        }
    }

    private static void drawNativeSplitBridge(
            Canvas canvas,
            boolean leftEdge,
            float baselineX,
            float bodyCenterX,
            float centerY,
            float bodyWidth,
            float bodyHeight,
            float splitProgress,
            float teardropFactor,
            float density,
            Paint fill,
            Paint border) {
        // Native renderer indexes the same teardrop profile with
        // ((split_progress * 0.1) + 0.2) * (count - 1).
        float profileT = clamp01(
                BRIDGE_PROFILE_BASE + splitProgress * BRIDGE_PROFILE_SPAN);
        int profileIndex = Math.min(
                OS4_PROFILE.length - 1,
                Math.max(0, Math.round(profileT * (OS4_PROFILE.length - 1))));
        float profileAmplitude = OS4_PROFILE[profileIndex][1] / PROFILE_WIDTH;

        // Native bridge half-width shrinks 8dp -> 1.5dp with split_progress.
        float bridgeHalfWidth = lerp(
                BRIDGE_HALF_WIDTH_START_DP * density,
                BRIDGE_HALF_WIDTH_END_DP * density,
                splitProgress);

        // Recovered bridge angle:
        // split * ((PI - asin(ratio)) - PI/4) + PI/4.
        // Express ratio in our already-resolved body/profile geometry so the Java port follows the
        // same normalized relation without depending on Rust's temporary raster bounds.
        float bodyHalfHeight = bodyHeight * 0.5f;
        float ratio = clamp01(
                (bodyHalfHeight * 0.25f * (1f - 0.85f * splitProgress))
                        / Math.max(bodyHalfHeight, 1f));
        float angle = splitProgress
                * (((float) Math.PI - (float) Math.asin(ratio))
                - ((float) Math.PI * 0.25f))
                + ((float) Math.PI * 0.25f);

        float direction = leftEdge ? 1f : -1f;
        float bodyEdgeX = leftEdge
                ? bodyCenterX + bodyWidth * 0.5f
                : bodyCenterX - bodyWidth * 0.5f;

        // Project the bridge root into the same teardrop profile used by build_teardrop_path.
        float rootX = baselineX
                + direction
                * Math.max(0f, bodyWidth * profileAmplitude * teardropFactor);
        float verticalReach = Math.max(
                bridgeHalfWidth,
                bodyHalfHeight * (float) Math.sin(angle));
        float topY = centerY - verticalReach;
        float bottomY = centerY + verticalReach;

        float outerX = bodyEdgeX;
        float control1X = rootX + (outerX - rootX) * splitProgress;
        float control2X = rootX + (outerX - rootX) * BRIDGE_CONTROL;

        Path path = PATH.get();
        path.reset();
        path.moveTo(rootX, centerY - bridgeHalfWidth);
        path.cubicTo(
                control1X,
                centerY - bridgeHalfWidth,
                control2X,
                centerY - bridgeHalfWidth,
                outerX,
                topY);
        path.lineTo(outerX, bottomY);
        path.cubicTo(
                control2X,
                centerY + bridgeHalfWidth,
                control1X,
                centerY + bridgeHalfWidth,
                rootX,
                centerY + bridgeHalfWidth);
        path.close();
        canvas.drawPath(path, fill);
        canvas.drawPath(path, border);
    }

    private static void buildNativeTeardropPath(
            Path path,
            float leftX,
            float rightX,
            float centerY,
            float profileHeightPx,
            boolean rightEdge,
            float verticalFactor,
            float horizontalFactor) {
        path.reset();

        float baselineX = rightEdge ? rightX : leftX;
        float halfHeight = profileHeightPx * 0.5f;
        float span = rightX - leftX;

        float previousX = 0f;
        float previousY = 0f;
        int count = OS4_PROFILE.length;

        for (int i = 0; i < count; i++) {
            float profileY = OS4_PROFILE[i][1];
            float displacement = (span / PROFILE_WIDTH) * profileY;
            float x = baselineX + (rightEdge ? -1f : 1f)
                    * horizontalFactor
                    * displacement;
            float y = centerY
                    + verticalFactor
                    * ((profileHeightPx / PROFILE_HEIGHT) * OS4_PROFILE[i][0] - halfHeight);

            if (i == 0) {
                path.moveTo(x, y);
            } else {
                float progress = (float) i / (float) count;
                float dx = x - previousX;
                float dy = y - previousY;

                float tangent;
                if (progress < 0.1f || progress > 0.9f) {
                    tangent = TANGENT_EDGE;
                } else if (progress > 0.4f && progress < 0.6f) {
                    tangent = TANGENT_MIDDLE;
                } else {
                    tangent = TANGENT_NORMAL;
                }

                float distanceSquared = dx * dx + dy * dy;
                if (distanceSquared > 0f) {
                    float ratio = Math.min(
                            (float) Math.sqrt(distanceSquared) / CUBIC_DISTANCE_NORMALIZER,
                            1f);
                    tangent *= ratio * 0.5f + 0.5f;
                }

                path.cubicTo(
                        previousX + dx * tangent,
                        previousY + dy * tangent,
                        x - dx * tangent,
                        y - dy * tangent,
                        x,
                        y);
            }

            previousX = x;
            previousY = y;
        }

        path.lineTo(baselineX, centerY + halfHeight);
        path.lineTo(baselineX, centerY - halfHeight);
        path.close();
    }

    private static float[][] buildNativeProfile() {
        int segments = OS4_SOURCE_PROFILE.length - 1;
        float[][] result = new float[segments * 5 + 1][2];
        int out = 0;

        for (int i = 0; i < segments; i++) {
            float[] p0 = OS4_SOURCE_PROFILE[i == 0 ? 0 : i - 1];
            float[] p1 = OS4_SOURCE_PROFILE[i];
            float[] p2 = OS4_SOURCE_PROFILE[i + 1];
            float[] p3 = OS4_SOURCE_PROFILE[i < segments - 1 ? i + 2 : i + 1];

            result[out][0] = p1[0];
            result[out][1] = p1[1];
            out++;

            for (int sample = 1; sample <= 4; sample++) {
                float t = sample / 5f;
                float t2 = t * t;
                float t3 = t2 * t;
                result[out][0] = catmullRom(p0[0], p1[0], p2[0], p3[0], t, t2, t3);
                result[out][1] = catmullRom(p0[1], p1[1], p2[1], p3[1], t, t2, t3);
                out++;
            }
        }

        result[out][0] = 775f;
        result[out][1] = 0f;
        return result;
    }

    private static float catmullRom(
            float p0,
            float p1,
            float p2,
            float p3,
            float t,
            float t2,
            float t3) {
        return 0.5f
                * (2f * p1
                + (p2 - p0) * t
                + (4f * p2 + 2f * p0 - 5f * p1 - p3) * t2
                + (3f * p1 - p0 - 3f * p2 + p3) * t3);
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

    private static float smoothStep(float edge0, float edge1, float x) {
        if (edge1 <= edge0) return x >= edge1 ? 1f : 0f;
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(value, 1f));
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }
}
