package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

/**
 * Source-level port of the OS4 Launcher 8.0 GestureBackArrowView Sidebar background.
 *
 * <p>The path kernel is translated from GestureBackArrowView::build_teardrop_path (0x801044).
 * The source control points and their Catmull-Rom expansion are translated from
 * GestureBackArrowView::create_background_proxy (0x7d7c88). PathBackgroundProxy::draw_teardrop
 * (0x7560c8) supplies the original fill + border paint pair.</p>
 *
 * <p>OS3 still owns the host View/Canvas and Security Center still owns the final Sidebar window;
 * no invented Bezier profile is used here.</p>
 */
final class Launcher450Os4SidebarConfirmationRenderer {
    private static final float START_WIDTH_DP = 30f;
    private static final float START_HEIGHT_DP = 30f;
    private static final float START_RADIUS_DP = 30f;
    private static final float TARGET_WIDTH_DP = 24f;
    private static final float TARGET_HEIGHT_DP = 53f;
    private static final float TARGET_RADIUS_DP = 8f;
    private static final float EDGE_SOURCE_OFFSET_DP = 12f;

    // Retained as animation state only. OS4 does not use this value as a direct X-coordinate lerp.
    private static final float RELEASE_SPLIT_TARGET = -1.5f;

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

    // build_teardrop_path native constants.
    private static final float PROFILE_HEIGHT = 775f;
    private static final float PROFILE_WIDTH = 76f;
    private static final float CUBIC_DISTANCE_NORMALIZER = 20f;
    private static final float TANGENT_EDGE = 0.08f;
    private static final float TANGENT_NORMAL = 0.15f;
    private static final float TANGENT_MIDDLE = 0.25f;

    // Exact source table at DAT_002f17d8, recovered by structural scan of the original profile.
    // create_background_proxy appends (775, 0) after interpolating these 16 segments.
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

    // Exact 81-point vector built by create_background_proxy.
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
            float gestureRawY,
            long startedAtUptimeMs,
            long releaseStartedAtUptimeMs) {
        if (canvas == null || arrowView == null) return;

        float density = arrowView.getResources().getDisplayMetrics().density;
        long now = SystemClock.uptimeMillis();
        float elapsedSeconds = Math.max(0L, now - startedAtUptimeMs) / 1000f;
        boolean releasing = releaseStartedAtUptimeMs > 0L;

        float widthProgress =
                springProgress(elapsedSeconds, WIDTH_DAMPING, WIDTH_RESPONSE_S);
        float heightProgress =
                springProgress(elapsedSeconds, HEIGHT_DAMPING, HEIGHT_RESPONSE_S);
        float radiusProgress =
                springProgress(elapsedSeconds, RADIUS_DAMPING, RADIUS_RESPONSE_S);
        float teardropFactor =
                springProgress(elapsedSeconds, TEARDROP_DAMPING, TEARDROP_RESPONSE_S);
        float bridgeFactor =
                springProgress(elapsedSeconds, BRIDGE_DAMPING, BRIDGE_RESPONSE_S);

        float width = lerp(
                START_WIDTH_DP * density,
                TARGET_WIDTH_DP * density,
                widthProgress);
        float height = lerp(
                START_HEIGHT_DP * density,
                TARGET_HEIGHT_DP * density,
                heightProgress);
        float radius = lerp(
                START_RADIUS_DP * density,
                TARGET_RADIUS_DP * density,
                radiusProgress);

        float viewWidth = arrowView.getWidth();
        float viewHeight = arrowView.getHeight();
        if (viewWidth <= 0f || viewHeight <= 0f) return;

        float sourceX = leftEdge
                ? -EDGE_SOURCE_OFFSET_DP * density
                : viewWidth + EDGE_SOURCE_OFFSET_DP * density;
        float targetX = leftEdge ? width * 0.5f : viewWidth - width * 0.5f;

        // OS4 split_progress is consumed by split_effect_renderer; it is not a direct position
        // lerp. The previous implementation extrapolated targetX with -1.5 and pushed the panel
        // offscreen. Once ACTION_UP begins, keep the Launcher source at its handoff endpoint.
        float centerX = releasing
                ? (leftEdge
                        ? TARGET_WIDTH_DP * density * 0.5f
                        : viewWidth - TARGET_WIDTH_DP * density * 0.5f)
                : lerp(sourceX, targetX, widthProgress);

        if (releasing) {
            width = TARGET_WIDTH_DP * density;
            height = TARGET_HEIGHT_DP * density;
            radius = TARGET_RADIUS_DP * density;
            teardropFactor = 1f;
            bridgeFactor = 1f;

            // Advance the recovered state for timing parity without treating it as a coordinate.
            float releaseSeconds =
                    Math.max(0L, now - releaseStartedAtUptimeMs) / 1000f;
            float releaseState = lerp(
                    1f,
                    RELEASE_SPLIT_TARGET,
                    springProgress(releaseSeconds, WIDTH_DAMPING, WIDTH_RESPONSE_S));
            if (Float.isNaN(releaseState)) return;
        }

        int[] location = new int[2];
        arrowView.getLocationOnScreen(location);
        float centerY = gestureRawY - location[1];
        float halfHeight = height * 0.5f;
        centerY = Math.max(halfHeight, Math.min(centerY, viewHeight - halfHeight));

        float halfWidth = width * 0.5f;
        float profileFarX;
        float profileLeft;
        float profileRight;
        if (leftEdge) {
            profileFarX = Math.max(0f, centerX + halfWidth);
            profileLeft = 0f;
            profileRight = profileFarX;
        } else {
            profileFarX = Math.min(viewWidth, centerX - halfWidth);
            profileLeft = profileFarX;
            profileRight = viewWidth;
        }

        if (profileRight - profileLeft > 0.001f && teardropFactor > 0.001f) {
            Path path = PATH.get();
            buildNativeTeardropPath(
                    path,
                    profileLeft,
                    profileRight,
                    centerY,
                    height,
                    !leftEdge,
                    bridgeFactor,
                    teardropFactor);

            // PathBackgroundProxy::draw_teardrop draws the same path with both native paints.
            canvas.drawPath(path, FILL_PAINT.get());
            canvas.drawPath(path, BORDER_PAINT.get());
        }

        // In OS4 the small rounded body belongs to the separated/split branch. Do not show our
        // panel during dwell; before ACTION_UP the Launcher-owned visual is the teardrop itself.
        if (releasing) {
            Paint fill = FILL_PAINT.get();
            canvas.drawRoundRect(
                    centerX - halfWidth,
                    centerY - halfHeight,
                    centerX + halfWidth,
                    centerY + halfHeight,
                    radius,
                    radius,
                    fill);
        }

        if (releasing || now - startedAtUptimeMs < MAX_FRAME_WINDOW_MS) {
            arrowView.postInvalidateOnAnimation();
        }
    }

    /**
     * Exact Java translation of GestureBackArrowView::build_teardrop_path @ 0x801044.
     *
     * @param leftX native param_1
     * @param rightX native param_2
     * @param centerY native param_3
     * @param verticalFactor native param_4
     * @param horizontalFactor native param_5
     */
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

    /**
     * Exact source-vector expansion from create_background_proxy @ 0x7d7c88.
     *
     * <p>For each of the 16 native segments, OS4 writes P1 then samples Catmull-Rom at
     * t={0.2,0.4,0.6,0.8}; after the loop it appends (775,0).</p>
     */
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

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }
}
