package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * UI-thread-only guard for reusing a stable icon/folder geometry snapshot.
 *
 * It compares the same View ancestry whose transforms are used by
 * transformMatrixToGlobal(), including parent identity, layout, scroll, alpha,
 * visibility and property matrices. It deliberately fails open for widgets,
 * native proxy geometry, transient/legacy animations, and unknown ancestry.
 * No timers or View invalidation hooks are installed.
 */
final class LauncherGlassStaticGeometryCache {
    private static final class ViewStamp {
        View view;
        final Matrix matrix = new Matrix();
        Matrix animationMatrix;
        int left, top, right, bottom, scrollX, scrollY, visibility;
        float alpha, translationX, translationY;

        boolean observe(View candidate) {
            Matrix candidateMatrix = candidate.getMatrix();
            boolean matrixChanged = !matrix.equals(candidateMatrix);
            // Transition.setAnimationMatrix is independent of View#getMatrix().
            // It can drive native per-frame geometry without changing normal properties.
            Matrix candidateAnimation = candidate.getAnimationMatrix();
            boolean animationChanged = (candidateAnimation == null) != (animationMatrix == null)
                    || (candidateAnimation != null
                    && !candidateAnimation.equals(animationMatrix));
            boolean changed = view != candidate
                    || left != candidate.getLeft() || top != candidate.getTop()
                    || right != candidate.getRight() || bottom != candidate.getBottom()
                    || scrollX != candidate.getScrollX() || scrollY != candidate.getScrollY()
                    || visibility != candidate.getVisibility()
                    || Float.compare(alpha, candidate.getAlpha()) != 0
                    || Float.compare(translationX, candidate.getTranslationX()) != 0
                    || Float.compare(translationY, candidate.getTranslationY()) != 0
                    || matrixChanged || animationChanged;
            view = candidate;
            left = candidate.getLeft();
            top = candidate.getTop();
            right = candidate.getRight();
            bottom = candidate.getBottom();
            scrollX = candidate.getScrollX();
            scrollY = candidate.getScrollY();
            visibility = candidate.getVisibility();
            alpha = candidate.getAlpha();
            translationX = candidate.getTranslationX();
            translationY = candidate.getTranslationY();
            if (matrixChanged) matrix.set(candidateMatrix);
            if (animationChanged) {
                if (candidateAnimation == null) animationMatrix = null;
                else {
                    if (animationMatrix == null) animationMatrix = new Matrix();
                    animationMatrix.set(candidateAnimation);
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

    void invalidate() {
        valid = false;
    }

    boolean unchanged(
            View material, View root, LauncherGlassDragState.Kind kind,
            GlassComponentStyle style, float nativeRadiusPx, float visibilityAlpha) {
        // Widget span adjustment reads mutable OEM fields; leave it on the original
        // full-capture path until it has a separately proven revision authority.
        if (kind == LauncherGlassDragState.Kind.WIDGET
                || material == null || root == null || !material.isAttachedToWindow()) {
            invalidate();
            return false;
        }

        boolean nowEnabled = style != null && style.enabled;
        boolean nowVisible = visibilityAlpha > 0.001f;
        float currentDensity = material.getResources().getDisplayMetrics().density;
        boolean dirty = !valid || rootWidth != root.getWidth() || rootHeight != root.getHeight()
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
            dirty |= ancestry.get(depth).observe(cursor);
            depth++;
            if (cursor.isLayoutRequested() || cursor.hasTransientState()
                    || (cursor.getAnimation() != null && !cursor.getAnimation().hasEnded())) {
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
        boolean reusable = valid && !dirty;
        // First non-transient frame must recapture, even if a Canvas animation
        // left the observed View properties unchanged from the previous frame.
        valid = !transientMotion;
        return reusable;
    }
}
