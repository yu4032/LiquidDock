package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.IdentityHashMap;

/**
 * UI-thread-only guard for reusing stable Launcher glass geometry snapshots.
 *
 * It compares the same View ancestry whose transforms are used by
 * transformMatrixToGlobal(), including parent identity, layout, scroll, alpha,
 * visibility and property matrices. Widget bounds additionally check the current
 * native span and computed horizontal width offset on every pre-draw. Native
 * proxies, transient/legacy animations and unknown ancestry fail open.
 * No timers or View invalidation hooks are installed.
 */
final class LauncherGlassStaticGeometryCache {
    /**
     * A consistent UI-thread view of each ancestor for ONE Workspace PreDraw.
     * Node-specific history remains in ViewStamp; no reading is retained as
     * authority between frames. Recycle slots to avoid hot-path allocations.
     */
    static final class Frame {
        private final IdentityHashMap<View, ViewSample> observed = new IdentityHashMap<>();
        private final ArrayList<ViewSample> samples = new ArrayList<>();
        private int used;
        int viewReads, sharedHits;

        void reset() {
            observed.clear();
            used = 0;
            viewReads = 0;
            sharedHits = 0;
        }

        ViewSample sample(View view) {
            ViewSample existing = observed.get(view);
            if (existing != null) {
                sharedHits++;
                return existing;
            }
            if (used == samples.size()) samples.add(new ViewSample());
            ViewSample next = samples.get(used++);
            next.read(view);
            observed.put(view, next);
            viewReads++;
            return next;
        }
    }

    private static final class ViewSample {
        final Matrix matrix = new Matrix();
        Matrix animationMatrix;
        int left, top, right, bottom, scrollX, scrollY, visibility;
        float alpha, translationX, translationY;
        boolean transientMotion;

        void read(View view) {
            matrix.set(view.getMatrix());
            Matrix animated = view.getAnimationMatrix();
            if (animated == null) {
                animationMatrix = null;
            } else {
                if (animationMatrix == null) animationMatrix = new Matrix();
                animationMatrix.set(animated);
            }
            left = view.getLeft();
            top = view.getTop();
            right = view.getRight();
            bottom = view.getBottom();
            scrollX = view.getScrollX();
            scrollY = view.getScrollY();
            visibility = view.getVisibility();
            alpha = view.getAlpha();
            translationX = view.getTranslationX();
            translationY = view.getTranslationY();
            android.view.animation.Animation animation = view.getAnimation();
            transientMotion = view.isLayoutRequested() || view.hasTransientState()
                    || (animation != null && !animation.hasEnded());
        }
    }

    private static final class ViewStamp {
        View view;
        final Matrix matrix = new Matrix();
        Matrix animationMatrix;
        int left, top, right, bottom, scrollX, scrollY, visibility;
        float alpha, translationX, translationY;

        boolean observe(View candidate, ViewSample current) {
            boolean matrixChanged = !matrix.equals(current.matrix);
            // Native Transition animationMatrix is independent from getMatrix.
            boolean animationChanged =
                    (current.animationMatrix == null) != (animationMatrix == null)
                    || (current.animationMatrix != null
                    && !current.animationMatrix.equals(animationMatrix));
            boolean changed = view != candidate
                    || left != current.left || top != current.top
                    || right != current.right || bottom != current.bottom
                    || scrollX != current.scrollX || scrollY != current.scrollY
                    || visibility != current.visibility
                    || Float.compare(alpha, current.alpha) != 0
                    || Float.compare(translationX, current.translationX) != 0
                    || Float.compare(translationY, current.translationY) != 0
                    || matrixChanged || animationChanged;
            view = candidate;
            left = current.left; top = current.top;
            right = current.right; bottom = current.bottom;
            scrollX = current.scrollX; scrollY = current.scrollY;
            visibility = current.visibility; alpha = current.alpha;
            translationX = current.translationX;
            translationY = current.translationY;
            if (matrixChanged) matrix.set(current.matrix);
            if (animationChanged) {
                if (current.animationMatrix == null) animationMatrix = null;
                else {
                    if (animationMatrix == null) animationMatrix = new Matrix();
                    animationMatrix.set(current.animationMatrix);
                }
            }
            return changed;
        }
    }

    // ShortcutIcon may change its compound drawable without changing the host bounds.
    // Observe only the inputs used by LauncherGlassIconGeometry.resolve().
    private static final class IconStamp {
        Drawable drawable;
        int paddingLeft, paddingTop, paddingRight, paddingBottom;
        int drawablePadding, lineHeight, compoundLeft, compoundRight;
        int boundLeft, boundTop, boundRight, boundBottom;
        int intrinsicWidth, intrinsicHeight;
        boolean textView;

        boolean observe(View host) {
            boolean isText = host instanceof TextView;
            TextView text = isText ? (TextView) host : null;
            Drawable top = null;
            if (text != null) {
                Drawable[] absolute = text.getCompoundDrawables();
                top = absolute != null && absolute.length > 1 ? absolute[1] : null;
                if (top == null) {
                    Drawable[] relative = text.getCompoundDrawablesRelative();
                    top = relative != null && relative.length > 1 ? relative[1] : null;
                }
            }
            Rect bounds = top != null ? top.getBounds() : null;
            int pl = host.getPaddingLeft(), pt = host.getPaddingTop();
            int pr = host.getPaddingRight(), pb = host.getPaddingBottom();
            int dp = text != null ? text.getCompoundDrawablePadding() : 0;
            int lh = text != null && top == null ? text.getLineHeight() : 0;
            int cl = text != null && top != null ? text.getCompoundPaddingLeft() : 0;
            int cr = text != null && top != null ? text.getCompoundPaddingRight() : 0;
            int bl = bounds != null ? bounds.left : 0;
            int bt = bounds != null ? bounds.top : 0;
            int br = bounds != null ? bounds.right : 0;
            int bb = bounds != null ? bounds.bottom : 0;
            int iw = top != null ? top.getIntrinsicWidth() : 0;
            int ih = top != null ? top.getIntrinsicHeight() : 0;
            boolean changed = textView != isText || drawable != top
                    || paddingLeft != pl || paddingTop != pt
                    || paddingRight != pr || paddingBottom != pb
                    || drawablePadding != dp || lineHeight != lh
                    || compoundLeft != cl || compoundRight != cr
                    || boundLeft != bl || boundTop != bt
                    || boundRight != br || boundBottom != bb
                    || intrinsicWidth != iw || intrinsicHeight != ih;
            textView = isText;
            drawable = top;
            paddingLeft = pl; paddingTop = pt; paddingRight = pr; paddingBottom = pb;
            drawablePadding = dp; lineHeight = lh; compoundLeft = cl; compoundRight = cr;
            boundLeft = bl; boundTop = bt; boundRight = br; boundBottom = bb;
            intrinsicWidth = iw; intrinsicHeight = ih;
            return changed;
        }
    }

    private final ArrayList<ViewStamp> ancestry = new ArrayList<>();
    private final IconStamp icon = new IconStamp();
    private boolean valid;
    private int rootWidth, rootHeight;
    private float radiusPx, sizeOffsetDp, styleRadiusDp, density;
    private boolean enabled, visible;
    private View widgetSpanOwner;
    private int widgetSpanX, widgetWidthDeltaPx;

    void invalidate() {
        valid = false;
    }

    boolean unchanged(
            View material, View root, LauncherGlassDragState.Kind kind,
            GlassComponentStyle style, float nativeRadiusPx, float visibilityAlpha,
            View currentWidgetSpanOwner, int currentWidgetSpanX,
            int currentWidgetWidthDeltaPx, Frame frame) {
        if (frame == null || material == null || root == null || !material.isAttachedToWindow()) {
            invalidate();
            return false;
        }

        boolean nowEnabled = style != null && style.enabled;
        boolean nowVisible = visibilityAlpha > 0.001f;
        float currentDensity = material.getResources().getDisplayMetrics().density;
        boolean dirty = !valid || rootWidth != root.getWidth() || rootHeight != root.getHeight()
                || (kind == LauncherGlassDragState.Kind.WIDGET
                && (widgetSpanOwner != currentWidgetSpanOwner
                || widgetSpanX != currentWidgetSpanX
                || widgetWidthDeltaPx != currentWidgetWidthDeltaPx))
                || enabled != nowEnabled || visible != nowVisible
                || Float.compare(radiusPx, nativeRadiusPx) != 0
                || Float.compare(sizeOffsetDp, style != null ? style.sizeOffsetDp : 0f) != 0
                || Float.compare(styleRadiusDp, style != null ? style.cornerRadiusDp : 0f) != 0
                || Float.compare(density, currentDensity) != 0;

        int depth = 0;
        View cursor = material;
        boolean reachedRoot = false;
        boolean transientMotion = false;
        while (cursor != null) {
            if (depth == ancestry.size()) ancestry.add(new ViewStamp());
            ViewSample current = frame.sample(cursor);
            dirty |= ancestry.get(depth).observe(cursor, current);
            depth++;
            if (current.transientMotion) {
                dirty = true;
                transientMotion = true; // These motions may bypass View property matrices.
            }
            if (cursor == root) {
                reachedRoot = true;
                break;
            }
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        if (!reachedRoot) {
            invalidate();
            return false;
        }
        while (ancestry.size() > depth) ancestry.remove(ancestry.size() - 1);
        if (kind == LauncherGlassDragState.Kind.ICON) dirty |= icon.observe(material);

        rootWidth = root.getWidth();
        rootHeight = root.getHeight();
        enabled = nowEnabled;
        visible = nowVisible;
        radiusPx = nativeRadiusPx;
        sizeOffsetDp = style != null ? style.sizeOffsetDp : 0f;
        styleRadiusDp = style != null ? style.cornerRadiusDp : 0f;
        density = currentDensity;
        if (kind == LauncherGlassDragState.Kind.WIDGET) {
            widgetSpanOwner = currentWidgetSpanOwner;
            widgetSpanX = currentWidgetSpanX;
            widgetWidthDeltaPx = currentWidgetWidthDeltaPx;
        }
        boolean reusable = valid && !dirty;
        // First non-transient frame must recapture, even if a Canvas animation
        // left the observed View properties unchanged from the previous frame.
        valid = !transientMotion;
        return reusable;
    }
}
