package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Path;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

/**
 * Lightweight geometry host for the zero-copy Prismal TextureView.
 *
 * <p>Prismal owns the visible glass silhouette and its derivative-antialiased edge. The host keeps
 * the matching outline only for RenderNode/MiShadow geometry; clipping the child a second time
 * would cut through the outer SDF coverage, especially on the last pixel row at some Dock sizes.</p>
 */
final class DockLiquidGlassHostView extends FrameLayout {
    private final Path outlinePath = new Path();
    private float radius;
    private boolean squircle;
    private float squircleCp = .58f;
    private boolean shapeDirty = true;

    DockLiquidGlassHostView(Context context) {
        super(context);
        // Keep the normal View.draw() path so a foreground StrokeDrawable is actually rendered.
        // This host still has no onDraw() body; the only local drawing is the foreground edge.
        setWillNotDraw(false);
        // The Prismal child is already alpha-masked by its SDF. Do not clip it again here: the
        // Android path clip is inset by half a pixel for outline/stroke alignment and would become
        // a competing coverage owner for the shader's outer AA/highlight pixels.
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Mi Shadow / RenderNode shadow geometry still follows the established Dock shape. This
        // outline is metadata for the shadow; it is deliberately not applied as a child clip.
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                ensureOutlinePath();
                if (outlinePath.isEmpty()) return;
                try {
                    outline.setPath(outlinePath);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    void setGeometry(float radius, boolean squircle, float cp) {
        float nextRadius = Math.max(0f, radius);
        float nextCp = Math.max(.05f, Math.min(.95f, cp));
        boolean changed = this.radius != nextRadius || this.squircle != squircle
                || this.squircleCp != nextCp;
        this.radius = nextRadius;
        this.squircle = squircle;
        this.squircleCp = nextCp;
        if (changed) {
            shapeDirty = true;
            DockStrokeRenderer.updateRadius(this, this.radius);
            invalidateOutline();
            invalidate();
        }
    }

    void setRadius(float radius) {
        setGeometry(radius, squircle, squircleCp);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
            shapeDirty = true;
            invalidateOutline();
        }
    }

    private void ensureOutlinePath() {
        if (!shapeDirty) return;
        outlinePath.rewind();
        if (getWidth() > 1 && getHeight() > 1) {
            DockShapePath.build(
                    outlinePath, getWidth(), getHeight(), radius, squircle, squircleCp);
        }
        shapeDirty = false;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        MiuixGlassHook.onHostAttached(this);
    }

    @Override protected void onDetachedFromWindow() {
        try {
            MiuixGlassHook.onHostDetached(this);
        } finally {
            super.onDetachedFromWindow();
        }
    }
}
