package com.hellovoid.liquiddock;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

/** Resolves only the icon graphic inside a label-bearing Launcher ShortcutIcon. */
final class LauncherGlassIconGeometry {
    static final class Bounds {
        final float left;
        final float top;
        final float right;
        final float bottom;

        Bounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        float width() { return Math.max(0f, right - left); }
        float height() { return Math.max(0f, bottom - top); }
    }

    private LauncherGlassIconGeometry() {}

    static Bounds resolve(View host) {
        if (host == null || host.getWidth() <= 0 || host.getHeight() <= 0) return null;
        if (host instanceof TextView) {
            TextView text = (TextView) host;
            Drawable[] drawables = text.getCompoundDrawables();
            Drawable top = drawables != null && drawables.length > 1 ? drawables[1] : null;
            if (top == null) {
                Drawable[] relative = text.getCompoundDrawablesRelative();
                top = relative != null && relative.length > 1 ? relative[1] : null;
            }
            Bounds fromDrawable = topDrawableBounds(text, top);
            if (fromDrawable != null) return fromDrawable;

            int availableWidth = Math.max(1,
                    host.getWidth() - host.getPaddingLeft() - host.getPaddingRight());
            int labelReserve = Math.max(0, text.getLineHeight())
                    + Math.max(0, text.getCompoundDrawablePadding());
            int availableHeight = Math.max(1,
                    host.getHeight() - host.getPaddingTop() - host.getPaddingBottom() - labelReserve);
            int side = Math.max(1, Math.min(availableWidth, availableHeight));
            return fallback(host.getWidth(), host.getHeight(), side, side, host.getPaddingTop());
        }
        int side = Math.max(1, Math.min(host.getWidth(), host.getHeight()));
        return fallback(host.getWidth(), host.getHeight(), side, side, host.getPaddingTop());
    }

    private static Bounds topDrawableBounds(TextView text, Drawable drawable) {
        if (text == null || drawable == null) return null;
        Rect b = drawable.getBounds();
        int width = b.width() > 0 ? b.width() : drawable.getIntrinsicWidth();
        int height = b.height() > 0 ? b.height() : drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0) return null;

        int boundLeft = b.width() > 0 ? b.left : 0;
        int boundTop = b.height() > 0 ? b.top : 0;
        int compoundLeft = text.getCompoundPaddingLeft();
        int compoundRight = text.getCompoundPaddingRight();
        int hspace = text.getWidth() - compoundRight - compoundLeft;
        float translateX = text.getScrollX() + compoundLeft + (hspace - width) * 0.5f;
        float translateY = text.getScrollY() + text.getPaddingTop();
        return clamp(text.getWidth(), text.getHeight(),
                translateX + boundLeft, translateY + boundTop,
                translateX + boundLeft + width, translateY + boundTop + height);
    }

    static Bounds fallback(
            int hostWidth, int hostHeight,
            int iconWidth, int iconHeight, int topOffset) {
        int safeHostWidth = Math.max(1, hostWidth);
        int safeHostHeight = Math.max(1, hostHeight);
        float top = Math.max(0f, Math.min(safeHostHeight - 1f, topOffset));
        float width = Math.max(1f, Math.min(safeHostWidth, iconWidth));
        float height = Math.max(1f, Math.min(safeHostHeight - top, iconHeight));
        float left = Math.max(0f, (safeHostWidth - width) * 0.5f);
        return clamp(safeHostWidth, safeHostHeight, left, top, left + width, top + height);
    }

    private static Bounds clamp(
            int hostWidth, int hostHeight,
            float left, float top, float right, float bottom) {
        float l = Math.max(0f, Math.min(hostWidth, left));
        float t = Math.max(0f, Math.min(hostHeight, top));
        float r = Math.max(l, Math.min(hostWidth, right));
        float b = Math.max(t, Math.min(hostHeight, bottom));
        return r > l && b > t ? new Bounds(l, t, r, b) : null;
    }
}
