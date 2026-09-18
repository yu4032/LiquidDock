package com.hellovoid.liquiddock;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Captures only the native time glyph alpha into a small root-coordinate mask bitmap. */
final class LockScreenClockGlyphMaskSource {
    static final class Mask {
        final Bitmap bitmap;
        final float left;
        final float top;
        final float width;
        final float height;
        final int rootWidth;
        final int rootHeight;
        final long signature;

        Mask(Bitmap bitmap, float left, float top, float width, float height,
             int rootWidth, int rootHeight, long signature) {
            this.bitmap = bitmap;
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.rootWidth = rootWidth;
            this.rootHeight = rootHeight;
            this.signature = signature;
        }
    }

    private final View clockRoot;
    private final List<View> glyphViews;

    LockScreenClockGlyphMaskSource(View clockRoot, List<View> glyphViews) {
        this.clockRoot = clockRoot;
        this.glyphViews = new ArrayList<>(glyphViews);
    }

    static LockScreenClockGlyphMaskSource resolve(View clockRoot) {
        if (clockRoot == null) return null;
        ArrayList<View> glyphs = new ArrayList<>();
        collectSemanticTimeViews(clockRoot, glyphs);
        if (glyphs.isEmpty()) return null;
        return new LockScreenClockGlyphMaskSource(clockRoot, glyphs);
    }

    Mask capture() {
        View windowRoot = clockRoot.getRootView();
        if (windowRoot == null || !windowRoot.isAttachedToWindow()
                || windowRoot.getWidth() <= 0 || windowRoot.getHeight() <= 0) return null;

        Matrix rootToGlobal = new Matrix();
        windowRoot.transformMatrixToGlobal(rootToGlobal);
        Matrix globalToRoot = new Matrix();
        if (!rootToGlobal.invert(globalToRoot)) return null;

        RectF union = null;
        ArrayList<Matrix> transforms = new ArrayList<>();
        ArrayList<View> visible = new ArrayList<>();
        long signature = 1469598103934665603L;

        for (View glyph : glyphViews) {
            if (glyph == null || !glyph.isAttachedToWindow()
                    || glyph.getWidth() <= 0 || glyph.getHeight() <= 0
                    || glyph.getVisibility() != View.VISIBLE) continue;
            Matrix localToRoot = new Matrix();
            glyph.transformMatrixToGlobal(localToRoot);
            localToRoot.postConcat(globalToRoot);

            RectF bounds = new RectF(0f, 0f, glyph.getWidth(), glyph.getHeight());
            localToRoot.mapRect(bounds);
            if (bounds.width() <= 0f || bounds.height() <= 0f) continue;
            if (union == null) union = new RectF(bounds);
            else union.union(bounds);
            transforms.add(localToRoot);
            visible.add(glyph);

            signature = mix(signature, glyph.getWidth());
            signature = mix(signature, glyph.getHeight());
            signature = mix(signature, Float.floatToIntBits(bounds.left));
            signature = mix(signature, Float.floatToIntBits(bounds.top));
            if (glyph instanceof TextView) {
                CharSequence text = ((TextView) glyph).getText();
                signature = mix(signature, text == null ? 0 : text.toString().hashCode());
            }
        }

        if (union == null || visible.isEmpty()) return null;
        int left = Math.max(0, (int) Math.floor(union.left));
        int top = Math.max(0, (int) Math.floor(union.top));
        int right = Math.min(windowRoot.getWidth(), (int) Math.ceil(union.right));
        int bottom = Math.min(windowRoot.getHeight(), (int) Math.ceil(union.bottom));
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) return null;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT);
        for (int i = 0; i < visible.size(); i++) {
            View glyph = visible.get(i);
            Matrix drawMatrix = new Matrix(transforms.get(i));
            drawMatrix.postTranslate(-left, -top);
            int save = canvas.save();
            canvas.concat(drawMatrix);
            glyph.draw(canvas);
            canvas.restoreToCount(save);
        }
        return new Mask(bitmap, left, top, width, height,
                windowRoot.getWidth(), windowRoot.getHeight(), signature);
    }

    RectF currentBounds() {
        Mask mask = capture();
        if (mask == null) return null;
        try {
            return new RectF(mask.left, mask.top, mask.left + mask.width, mask.top + mask.height);
        } finally {
            mask.bitmap.recycle();
        }
    }

    private static void collectSemanticTimeViews(View root, List<View> out) {
        String className = root.getClass().getName();
        boolean semanticTimeView = className.equals("com.miui.clock.MiuiTextGlassView")
                || className.endsWith(".TimeView");
        if (semanticTimeView && root instanceof TextView) {
            CharSequence text = ((TextView) root).getText();
            if (text != null && text.length() > 0) out.add(root);
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectSemanticTimeViews(group.getChildAt(i), out);
            }
        }
    }

    private static long mix(long hash, int value) {
        hash ^= value;
        return hash * 1099511628211L;
    }
}
