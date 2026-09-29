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
    // on_vsync's standalone mini-Sidebar layer starts with its center 12dp outside the edge.
    private static final float MINI_SIDEBAR_OUTSIDE_CENTER_DP = 12f;
    // GestureBackArrowView constructor maps +0x1f0 to dp_to_px(30). on_vsync places the
    // separated mini Sidebar at calculate_positions endpoint +/- this exact offset.
    private static final float MINI_SIDEBAR_TARGET_OFFSET_DP = 30f;
    // Exact CachedIconDp initializer recovered from OS4 Launcher:
    // dp_to_px(9), dp_to_px(3), dp_to_px(6), stored as item size, corner radius, gap.
    private static final float SIDEBAR_ICON_DOT_DP = 9f;
    private static final float SIDEBAR_ICON_RADIUS_DP = 3f;
    private static final float SIDEBAR_ICON_GAP_DP = 6f;

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
    private static final ThreadLocal<Paint> ICON_PAINT =
            ThreadLocal.withInitial(() -> {
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFFFFFFF);
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
            float arrowBackWidth,
            float gestureProgress,
            boolean splitActive,
            long splitStartedAtUptimeMs,
            float gestureRawY,
            long startedAtUptimeMs,
            long releaseStartedAtUptimeMs) {
        if (canvas == null || arrowView == null) return;

        float density = os4Density(arrowView);
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

        boolean splitGateReached = splitActive;

        // OS4 uses the 0.8 gesture threshold as the gate that starts a show=true Folme target.
        // After the gate is crossed, split_progress itself animates to 1 independently of any
        // further pointer distance.
        float splitSeconds = splitGateReached && splitStartedAtUptimeMs > 0L
                ? Math.max(0L, now - splitStartedAtUptimeMs) / 1000f
                : 0f;

        splitProgress = currentSplitProgress(
                splitGateReached,
                splitStartedAtUptimeMs,
                releaseStartedAtUptimeMs,
                now);

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

        // Exact cross-version position mapping:
        // OS4 calculate_positions() uses the current gesture-background endpoint. OS3's own
        // GestureBackArrowView.onDraw() defines that same endpoint as:
        //   left:  mStartX + mBackWidth * mScale
        //   right: viewWidth - (mStartX + mBackWidth * mScale)
        // where mScale == GesturesBackController.convertOffset(offset) / 20.
        // The OS4 separated body then starts with its center 12dp outside the display and lerps
        // to one body half-width past that endpoint.
        float miniCenterX = resolveMiniSidebarCenterX(
                viewWidth,
                leftEdge,
                baselineX,
                arrowBackWidth,
                gestureProgress,
                splitProgress,
                density);

        // Teardrop remains rooted at the original gesture baseline.
        float teardropCenterX = leftEdge
                ? baselineX + halfWidth
                : baselineX - halfWidth;

        // The water-drop path shares the same baseline as OS3's stock Back background.
        if (teardropFactor > 0.001f && bridgeFactor > 0.001f) {
            float profileLeft = leftEdge ? baselineX : teardropCenterX - halfWidth;
            float profileRight = leftEdge ? teardropCenterX + halfWidth : baselineX;
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
                    miniCenterX,
                    centerY,
                    width,
                    height,
                    radius,
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
            int oldFillAlpha = fill.getAlpha();
            fill.setAlpha(Math.round(oldFillAlpha * reveal));

            // Exact standalone mini-Sidebar geometry from GestureBackArrowView::on_vsync:
            // 24dp x 53dp, radius 8dp. It is independent from the split renderer's intermediate
            // sidebar_width/sidebar_height values.
            float miniHalfWidth = SIDEBAR_WIDTH_DP * density * 0.5f;
            float miniHalfHeight = SIDEBAR_HEIGHT_DP * density * 0.5f;
            float miniRadius = SIDEBAR_RADIUS_DP * density;
            canvas.drawRoundRect(
                    miniCenterX - miniHalfWidth,
                    centerY - miniHalfHeight,
                    miniCenterX + miniHalfWidth,
                    centerY + miniHalfHeight,
                    miniRadius,
                    miniRadius,
                    fill);
            // OS4's standalone separated body branch is fill-only (0xCC000000); the
            // 0x20000000 stroke belongs to PathBackgroundProxy's teardrop path, not this body.
            drawNativeMiniSidebarIcon(canvas, miniCenterX, centerY, reveal, density);

            fill.setAlpha(oldFillAlpha);
        }

        if (releasing
                || now - startedAtUptimeMs < MAX_FRAME_WINDOW_MS
                || splitProgress < 0.999f
                || teardropFactor > 0.001f) {
            arrowView.postInvalidateOnAnimation();
        }
    }

    static float os4Density(View view) {
        if (view == null || view.getResources() == null) return 1f;
        int densityDpi = view.getResources().getConfiguration().densityDpi;
        return densityDpi > 0 ? densityDpi / 160f
                : view.getResources().getDisplayMetrics().density;
    }

    static float currentMiniSidebarCenterX(
            View arrowView,
            boolean leftEdge,
            float arrowStartX,
            float arrowBackWidth,
            float gestureProgress,
            boolean splitActive,
            long splitStartedAtUptimeMs,
            long releaseStartedAtUptimeMs) {
        if (arrowView == null) return Float.NaN;
        float viewWidth = arrowView.getWidth();
        if (viewWidth <= 0f) return Float.NaN;
        float density = os4Density(arrowView);
        float baselineX = !Float.isNaN(arrowStartX)
                ? (leftEdge ? arrowStartX : viewWidth - arrowStartX)
                : (leftEdge
                        ? FALLBACK_EDGE_INSET_DP * density
                        : viewWidth - FALLBACK_EDGE_INSET_DP * density);
        float splitProgress = currentSplitProgress(
                splitActive,
                splitStartedAtUptimeMs,
                releaseStartedAtUptimeMs,
                SystemClock.uptimeMillis());
        return resolveMiniSidebarCenterX(
                viewWidth,
                leftEdge,
                baselineX,
                arrowBackWidth,
                gestureProgress,
                splitProgress,
                density);
    }

    private static float currentSplitProgress(
            boolean splitActive,
            long splitStartedAtUptimeMs,
            long releaseStartedAtUptimeMs,
            long now) {
        float split = 0f;
        if (splitActive && splitStartedAtUptimeMs > 0L) {
            float seconds = Math.max(0L, now - splitStartedAtUptimeMs) / 1000f;
            split = clamp01(springProgress(seconds, SPLIT_DAMPING, SPLIT_RESPONSE_S));
        }
        if (releaseStartedAtUptimeMs > 0L) {
            float seconds = Math.max(0L, now - releaseStartedAtUptimeMs) / 1000f;
            float release = clamp01(springProgress(seconds, SPLIT_DAMPING, SPLIT_RESPONSE_S));
            split = Math.max(split, release);
        }
        return split;
    }

    private static float resolveMiniSidebarCenterX(
            float viewWidth,
            boolean leftEdge,
            float baselineX,
            float arrowBackWidth,
            float gestureProgress,
            float splitProgress,
            float density) {
        float nativeBackWidth =
                !Float.isNaN(arrowBackWidth) && arrowBackWidth > 0f
                        ? arrowBackWidth
                        : PROFILE_WIDTH * density;
        float projectedGestureProgress = Math.min(clamp01(gestureProgress), SPLIT_START_PROGRESS);
        float gestureExtent = nativeBackWidth * projectedGestureProgress;
        float gestureEndpointX = leftEdge
                ? baselineX + gestureExtent
                : baselineX - gestureExtent;
        float targetOffset = MINI_SIDEBAR_TARGET_OFFSET_DP * density;
        float edgeClamp = MINI_SIDEBAR_OUTSIDE_CENTER_DP * density;
        float targetCenterX = leftEdge
                ? Math.min(gestureEndpointX + targetOffset, viewWidth - edgeClamp)
                : Math.max(edgeClamp, gestureEndpointX - targetOffset);
        float startCenterX = leftEdge
                ? -MINI_SIDEBAR_OUTSIDE_CENTER_DP * density
                : viewWidth + MINI_SIDEBAR_OUTSIDE_CENTER_DP * density;
        return lerp(startCenterX, targetCenterX, clamp01(splitProgress));
    }

    private static void drawNativeMiniSidebarIcon(
            Canvas canvas,
            float centerX,
            float centerY,
            float reveal,
            float density) {
        // on_vsync renders the cached Sidebar icon with three white rounded rects centered
        // vertically inside the 24x53 body.
        float dot = SIDEBAR_ICON_DOT_DP * density;
        float radius = SIDEBAR_ICON_RADIUS_DP * density;
        float step = (SIDEBAR_ICON_DOT_DP + SIDEBAR_ICON_GAP_DP) * density;
        float halfDot = dot * 0.5f;

        Paint icon = ICON_PAINT.get();
        int oldAlpha = icon.getAlpha();
        icon.setAlpha(Math.round(255f * clamp01(reveal)));
        for (int i = -1; i <= 1; i++) {
            float cy = centerY + i * step;
            canvas.drawRoundRect(
                    centerX - halfDot,
                    cy - halfDot,
                    centerX + halfDot,
                    cy + halfDot,
                    radius,
                    radius,
                    icon);
        }
        icon.setAlpha(oldAlpha);
    }

    private static void drawNativeSplitBridge(
            Canvas canvas,
            boolean leftEdge,
            float baselineX,
            float bodyCenterX,
            float centerY,
            float bodyWidth,
            float bodyHeight,
            float bodyRadius,
            float splitProgress,
            float teardropFactor,
            float density,
            Paint fill,
            Paint border) {
        // Direct translation of the closed bridge path in PathBackgroundProxy::draw @ 0x9bdea8.
        // The native renderer samples the teardrop profile at:
        // ((split_progress * 0.1) + 0.2) * (count - 1).
        float profileT = clamp01(
                BRIDGE_PROFILE_BASE + splitProgress * BRIDGE_PROFILE_SPAN);
        int profileIndex = Math.min(
                OS4_PROFILE.length - 1,
                Math.max(0, (int) (profileT * (OS4_PROFILE.length - 1))));
        float profileX = OS4_PROFILE[profileIndex][0];
        float profileY = OS4_PROFILE[profileIndex][1];

        float direction = leftEdge ? 1f : -1f;
        float bodyHalfWidth = bodyWidth * 0.5f;
        float bodyHalfHeight = bodyHeight * 0.5f;

        // fVar56 = split * -0.72 + 1.0, followed by the current sidebar-height/profile mapping.
        float profileVerticalScale = 1f - 0.72f * splitProgress;
        float profileOffsetY = profileVerticalScale
                * ((bodyHeight / PROFILE_HEIGHT) * profileX - bodyHeight * 0.5f);
        float rootY0 = centerY + profileOffsetY;
        float rootY1 = centerY - profileOffsetY;
        float rootTop = Math.min(rootY0, rootY1);
        float rootBottom = Math.max(rootY0, rootY1);

        // Same horizontal profile projection used by build_teardrop_path.
        float rootX = baselineX
                + direction
                * (bodyWidth / PROFILE_WIDTH)
                * profileY
                * teardropFactor;

        // Native radius/angle preparation:
        // quarter span -> max(0.3 * span, (1 - 0.85*split) * span)
        // -> asin(normalized by body half-width)
        // -> split * ((PI - asin) - PI/4) + PI/4.
        float profileQuarterSpan = Math.abs(rootBottom - rootTop) * 0.25f;
        float angleNumerator = Math.max(
                profileQuarterSpan * 0.3f,
                profileQuarterSpan * (1f - 0.85f * splitProgress));
        float ratio = clamp01(angleNumerator / Math.max(bodyHalfWidth, 1f));
        float angle = splitProgress
                * (((float) Math.PI - (float) Math.asin(ratio))
                - ((float) Math.PI * 0.25f))
                + ((float) Math.PI * 0.25f);

        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float outerX = bodyCenterX + direction * bodyHalfWidth * cos;

        float bodyTop = centerY - bodyHalfHeight;
        float bodyBottom = centerY + bodyHalfHeight;
        float outerTop = Math.max(
                centerY - bodyHalfHeight * sin,
                bodyTop + bodyRadius * 0.3f);
        float outerBottom = Math.min(
                centerY + bodyHalfHeight * sin,
                bodyBottom - bodyRadius * 0.3f);

        // Decompiled path uses 0.3 and 0.7 horizontal control interpolation.
        float controlX1 = rootX + (outerX - rootX) * 0.3f;
        float controlX2 = rootX + (outerX - rootX) * BRIDGE_CONTROL;

        // Native bridge neck half-width shrinks 8dp -> 1.5dp with split_progress.
        float bridgeHalfWidth = lerp(
                BRIDGE_HALF_WIDTH_START_DP * density,
                BRIDGE_HALF_WIDTH_END_DP * density,
                splitProgress);

        Path path = PATH.get();
        path.reset();
        path.moveTo(rootX, rootTop);
        path.cubicTo(
                controlX1,
                centerY - bridgeHalfWidth,
                controlX2,
                centerY - bridgeHalfWidth,
                outerX,
                outerTop);
        path.lineTo(outerX, outerBottom);
        path.cubicTo(
                controlX2,
                centerY + bridgeHalfWidth,
                controlX1,
                centerY + bridgeHalfWidth,
                rootX,
                rootBottom);
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
