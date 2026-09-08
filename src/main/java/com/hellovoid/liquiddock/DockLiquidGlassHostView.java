package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Path;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

/** Lightweight geometry host for the zero-copy Prismal TextureView. */
final class DockLiquidGlassHostView extends FrameLayout {
    private final Path clipPath = new Path();
    private float radius;
    private boolean squircle;
    private float squircleCp = .58f;
    private boolean shapeDirty = true;

    DockLiquidGlassHostView(Context context) {
        super(context);
        // Keep the normal View.draw() path so a configured foreground/shadow can still render,
        // but do not clip the TextureView here. Prismal geometry is the sole visible Dock shape
        // authority, matching the Workspace glass path and avoiding a second AA boundary.
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Mi Shadow / RenderNode shadow geometry may still use the View outline. The outline is not
        // used to clip children; it only supplies native shadow geometry outside the Prismal body.
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                ensureClipPath();
                if (clipPath.isEmpty()) return;
                try {
                    outline.setPath(clipPath);
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

    private void ensureClipPath() {
        if (!shapeDirty) return;
        clipPath.rewind();
        if (getWidth() > 1 && getHeight() > 1) {
            DockShapePath.build(clipPath, getWidth(), getHeight(), radius, squircle, squircleCp);
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
