package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

/** Lightweight geometry host for the zero-copy Prismal TextureView. */
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
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Prismal is the one visible alpha-mask authority. The View outline is shadow geometry
        // only, so it must use Prismal's full zero-contour rather than DockShapePath's .5px
        // pixel-center inset. No child clipping is performed with this path.
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
            // Do not implicitly mutate the foreground stroke here. Launcher 4.50 feeds this host
            // intermediate radius values during workstation exit. MiuixGlassHook commits stroke
            // geometry explicitly only after the vendor radius animator is authoritative again.
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
        if (getWidth() > 0 && getHeight() > 0) {
            DockPrismalOutlinePath.build(
                    outlinePath, getWidth(), getHeight(), radius, squircle, squircleCp);
        }
        shapeDirty = false;
    }

    @Override protected void onDetachedFromWindow() {
        try {
            MiuixGlassHook.onHostDetached(this);
        } finally {
            super.onDetachedFromWindow();
        }
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        // Prismal already produces the final rounded alpha mask. Clipping that TextureView again
        // makes two independently antialiased masks meet at the same pixel boundary; on long
        // horizontal edges their subpixel phases show up as a dotted white seam. The cached path
        // remains the authoritative View outline for native shadow geometry only.
        super.dispatchDraw(canvas);
    }
}
