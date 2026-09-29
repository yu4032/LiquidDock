package com.hellovoid.liquiddock;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

/**
 * Launcher-side visual bridge between the back gesture and Security Center Sidebar handoff.
 *
 * <p>HyperOS 4 Launcher 8.0 renders this stage in its Rust back-panel pipeline rather than in
 * Security Center. Native evidence shows a #CC000000 24x53dp rounded rect with 8dp radius drawn
 * from GestureBackArrowView::on_vsync while the SideSlideHold state is active. The native back
 * arrow transition uses 100 ms entering and 50 ms leaving; this view mirrors those observable
 * contracts without pretending to run the OS4 Rust/Folme implementation on the OS3 Java launcher.</p>
 */
final class LauncherSideSlidePreview extends View {
    static final float PANEL_WIDTH_DP = 24f;
    static final float PANEL_HEIGHT_DP = 53f;
    static final float PANEL_RADIUS_DP = 8f;
    static final float HANDOFF_WIDTH_DP = 30f;
    static final long ENTER_DURATION_MS = 100L;
    static final long RELEASE_DURATION_MS = 50L;
    static final int PANEL_COLOR = 0xCC000000;

    private final ViewGroup host;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final boolean leftEdge;
    private final float centerY;
    private final float density;

    private float revealProgress;
    private float handoffProgress;
    private boolean releasing;
    private boolean handoffAcknowledged;
    private ValueAnimator animator;
    private boolean removed;

    private LauncherSideSlidePreview(
            Context context, ViewGroup host, boolean leftEdge, float centerY) {
        super(context);
        this.host = host;
        this.leftEdge = leftEdge;
        this.centerY = centerY;
        this.density = context.getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PANEL_COLOR);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
    }

    static LauncherSideSlidePreview show(
            View owner, boolean leftEdge, float localCenterY) {
        if (!(owner instanceof ViewGroup)) return null;
        ViewGroup host = (ViewGroup) owner;
        LauncherSideSlidePreview preview =
                new LauncherSideSlidePreview(owner.getContext(), host, leftEdge, localCenterY);
        host.addView(preview, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        preview.bringToFront();
        preview.startReveal();
        return preview;
    }

    void beginRelease() {
        if (removed || releasing) return;
        releasing = true;
        cancelAnimator();
        ValueAnimator release = ValueAnimator.ofFloat(handoffProgress, 1f);
        release.setDuration(RELEASE_DURATION_MS);
        release.setInterpolator(new LinearInterpolator());
        release.addUpdateListener(animation -> {
            handoffProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        release.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (handoffAcknowledged) removeNow();
            }
        });
        animator = release;
        release.start();
    }

    void finishHandoff(boolean accepted) {
        if (removed) return;
        if (!accepted) {
            cancel();
            return;
        }
        handoffAcknowledged = true;
        if (!releasing || handoffProgress >= 0.999f) {
            removeNow();
        }
    }

    void cancel() {
        if (removed) return;
        cancelAnimator();
        final float start = revealProgress;
        ValueAnimator retreat = ValueAnimator.ofFloat(start, 0f);
        retreat.setDuration(RELEASE_DURATION_MS);
        retreat.setInterpolator(new LinearInterpolator());
        retreat.addUpdateListener(animation -> {
            revealProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        retreat.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                removeNow();
            }
        });
        animator = retreat;
        retreat.start();
    }

    private void startReveal() {
        revealProgress = 0f;
        handoffProgress = 0f;
        ValueAnimator reveal = ValueAnimator.ofFloat(0f, 1f);
        reveal.setDuration(ENTER_DURATION_MS);
        reveal.setInterpolator(new LinearInterpolator());
        reveal.addUpdateListener(animation -> {
            revealProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator = reveal;
        reveal.start();
    }

    private void cancelAnimator() {
        ValueAnimator current = animator;
        animator = null;
        if (current != null) {
            current.removeAllListeners();
            current.cancel();
        }
    }

    private void removeNow() {
        if (removed) return;
        removed = true;
        cancelAnimator();
        if (getParent() == host) {
            host.removeView(this);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (removed || getWidth() <= 0 || getHeight() <= 0) return;

        float previewWidth = PANEL_WIDTH_DP * density;
        float handoffWidth = HANDOFF_WIDTH_DP * density;
        float width = previewWidth + (handoffWidth - previewWidth) * handoffProgress;
        float height = PANEL_HEIGHT_DP * density;
        float radius = PANEL_RADIUS_DP * density;
        float halfWidth = width * 0.5f;
        float halfHeight = height * 0.5f;

        // OS4 starts the black pill outside the edge and advances it with split_progress.
        // Keeping the center on +/- halfWidth means progress=0 is fully clipped and progress=1
        // is exactly flush with the gesture edge.
        float hiddenCenterX = leftEdge ? -halfWidth : getWidth() + halfWidth;
        float shownCenterX = leftEdge ? halfWidth : getWidth() - halfWidth;
        float centerX = hiddenCenterX + (shownCenterX - hiddenCenterX) * revealProgress;

        float clampedCenterY = Math.max(halfHeight, Math.min(centerY, getHeight() - halfHeight));
        rect.set(
                centerX - halfWidth,
                clampedCenterY - halfHeight,
                centerX + halfWidth,
                clampedCenterY + halfHeight);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }
}
