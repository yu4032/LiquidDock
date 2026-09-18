package com.hellovoid.liquiddock;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

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
    private final WeakHashMap<View, Float> originalAlpha = new WeakHashMap<>();
    private final WeakHashMap<View, Integer> originalVisibility = new WeakHashMap<>();
    private long lastSignature = Long.MIN_VALUE;
    private long lastContentSignature = Long.MIN_VALUE;
    private int lastObservedLeft = Integer.MIN_VALUE;
    private int lastObservedTop = Integer.MIN_VALUE;
    private int lastObservedRight = Integer.MIN_VALUE;
    private int lastObservedBottom = Integer.MIN_VALUE;
    private int stableGeometryFrames;

    LockScreenClockGlyphMaskSource(View clockRoot, List<View> glyphViews) {
        this.clockRoot = clockRoot;
        this.glyphViews = new ArrayList<>(glyphViews);
    }

    static LockScreenClockGlyphMaskSource resolve(View clockRoot) {
        if (clockRoot == null) return null;
        ArrayList<View> glyphs = new ArrayList<>();

        // OS3 Classic clock implementations expose semantic, non-obfuscated time fields.
        // Prefer those over tree timing/resource-name discovery because addClockView() can run
        // while inflation/updateTime are still settling.
        addSemanticField(clockRoot, "mTimeView", glyphs);
        addSemanticField(clockRoot, "mTimeView2", glyphs);
        addSemanticField(clockRoot, "mHourTextStyle1", glyphs);
        addSemanticField(clockRoot, "mHourTextStyle2", glyphs);
        addSemanticField(clockRoot, "mMinuteTextStyle1", glyphs);
        addSemanticField(clockRoot, "mMinuteTextStyle2", glyphs);

        // Resource semantics cover the remaining OS3 clock families without depending on
        // decompiler-generated class/member names.
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
                    || glyph.getWidth() <= 0 || glyph.getHeight() <= 0) continue;
            Integer nativeVisibility = originalVisibility.get(glyph);
            int effectiveVisibility = nativeVisibility != null
                    ? nativeVisibility : glyph.getVisibility();
            if (effectiveVisibility != View.VISIBLE) continue;
            Matrix localToGlobal = new Matrix();
            glyph.transformMatrixToGlobal(localToGlobal);
            Matrix localToRoot = new Matrix();
            localToRoot.setConcat(globalToRoot, localToGlobal);

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

        long contentSignature = 1469598103934665603L;
        for (View glyph : visible) {
            if (glyph instanceof TextView) {
                CharSequence text = ((TextView) glyph).getText();
                contentSignature = mix(contentSignature, text == null ? 0 : text.toString().hashCode());
            }
            contentSignature = mix(contentSignature, glyph.getWidth());
            contentSignature = mix(contentSignature, glyph.getHeight());
        }

        boolean sameObservedGeometry = left == lastObservedLeft && top == lastObservedTop
                && right == lastObservedRight && bottom == lastObservedBottom;
        if (sameObservedGeometry) {
            stableGeometryFrames++;
        } else {
            lastObservedLeft = left;
            lastObservedTop = top;
            lastObservedRight = right;
            lastObservedBottom = bottom;
            stableGeometryFrames = 0;
        }

        // Do not publish a replacement mask while the native clock is still transforming.
        // Two consecutive identical frame observations form a semantic stability barrier without
        // an arbitrary wall-clock delay. Text changes bypass the barrier so minute updates remain live.
        boolean contentChanged = contentSignature != lastContentSignature;
        if (lastContentSignature == Long.MIN_VALUE) {
            if (stableGeometryFrames < 2) return null;
        } else if (contentChanged) {
            if (stableGeometryFrames < 1) return null;
        } else if (stableGeometryFrames < 2) {
            return null;
        }
        lastContentSignature = contentSignature;

        signature = mix(signature, left);
        signature = mix(signature, top);
        signature = mix(signature, right);
        signature = mix(signature, bottom);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT);
        for (int i = 0; i < visible.size(); i++) {
            View glyph = visible.get(i);
            Matrix drawMatrix = new Matrix(transforms.get(i));
            drawMatrix.postTranslate(-left, -top);
            int save = canvas.save();
            canvas.concat(drawMatrix);
            float currentAlpha = glyph.getAlpha();
            int currentVisibility = glyph.getVisibility();
            Float nativeAlpha = originalAlpha.get(glyph);
            Integer nativeVisibility = originalVisibility.get(glyph);
            float captureAlpha = nativeAlpha != null ? nativeAlpha : currentAlpha;
            int captureVisibility = nativeVisibility != null ? nativeVisibility : currentVisibility;
            if (captureAlpha <= 0f) captureAlpha = 1f;
            if (captureVisibility != View.VISIBLE) captureVisibility = View.VISIBLE;
            if (currentVisibility != captureVisibility) glyph.setVisibility(captureVisibility);
            if (currentAlpha != captureAlpha) glyph.setAlpha(captureAlpha);
            try {
                glyph.draw(canvas);
            } finally {
                if (glyph.getAlpha() != currentAlpha) glyph.setAlpha(currentAlpha);
                if (glyph.getVisibility() != currentVisibility) glyph.setVisibility(currentVisibility);
                canvas.restoreToCount(save);
            }
        }
        int[] tight = findAlphaBounds(bitmap);
        if (tight == null) {
            bitmap.recycle();
            return null;
        }
        int tightLeft = tight[0];
        int tightTop = tight[1];
        int tightRight = tight[2];
        int tightBottom = tight[3];
        int tightWidth = tightRight - tightLeft;
        int tightHeight = tightBottom - tightTop;
        if (tightWidth <= 0 || tightHeight <= 0) {
            bitmap.recycle();
            return null;
        }

        Bitmap tightBitmap = bitmap;
        if (tightLeft != 0 || tightTop != 0 || tightWidth != width || tightHeight != height) {
            tightBitmap = Bitmap.createBitmap(bitmap, tightLeft, tightTop, tightWidth, tightHeight);
            bitmap.recycle();
        }
        float tightRootLeft = left + tightLeft;
        float tightRootTop = top + tightTop;
        long tightSignature = mix(mix(signature, tightLeft), tightTop);
        tightSignature = mix(mix(tightSignature, tightWidth), tightHeight);
        if (tightSignature == lastSignature) {
            tightBitmap.recycle();
            return null;
        }
        lastSignature = tightSignature;
        return new Mask(tightBitmap, tightRootLeft, tightRootTop, tightWidth, tightHeight,
                windowRoot.getWidth(), windowRoot.getHeight(), tightSignature);
    }

    void suppressNativeGlyphs() {
        for (View glyph : glyphViews) {
            if (glyph == null) continue;
            if (!originalAlpha.containsKey(glyph)) originalAlpha.put(glyph, glyph.getAlpha());
            if (!originalVisibility.containsKey(glyph)) {
                originalVisibility.put(glyph, glyph.getVisibility());
            }
            // INVISIBLE preserves layout/measurement but removes the native clock pixels entirely.
            glyph.setVisibility(View.INVISIBLE);
        }
    }

    void restoreNativeGlyphs() {
        ArrayList<View> views = new ArrayList<>(originalVisibility.keySet());
        for (View glyph : views) {
            Integer visibility = originalVisibility.get(glyph);
            Float alpha = originalAlpha.get(glyph);
            if (glyph == null) continue;
            if (alpha != null) glyph.setAlpha(alpha);
            if (visibility != null) glyph.setVisibility(visibility);
        }
        originalVisibility.clear();
        originalAlpha.clear();
    }

    int glyphCount() {
        return glyphViews.size();
    }


    private static void addSemanticField(View clockRoot, String fieldName, List<View> out) {
        Field field = findField(clockRoot.getClass(), fieldName);
        if (field == null) return;
        try {
            field.setAccessible(true);
            Object value = field.get(clockRoot);
            if (!(value instanceof TextView)) return;
            View view = (View) value;
            if (!out.contains(view)) out.add(view);
        } catch (Throwable ignored) {}
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static void collectSemanticTimeViews(View root, List<View> out) {
        String resourceName = resourceEntryName(root);
        boolean semanticTimeId = isTimeResourceName(resourceName);

        // Only text-bearing time/hour/minute nodes are replaced. Date, week, weather and other
        // clock decorations stay native even when they share the same overall clock container.
        if (root instanceof TextView && semanticTimeId && !out.contains(root)) out.add(root);

        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectSemanticTimeViews(group.getChildAt(i), out);
            }
        }
    }

    private static boolean isTimeResourceName(String name) {
        if (name == null || name.isEmpty()) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("date") || lower.contains("week") || lower.contains("weather")
                || lower.contains("signature") || lower.contains("notification")) return false;
        return lower.equals("time_view") || lower.equals("time_view2")
                || lower.equals("time") || lower.equals("tv_time")
                || lower.contains("time_hour") || lower.contains("time_minute")
                || lower.contains("hour_text") || lower.contains("minute_text")
                || lower.equals("tv_hour") || lower.equals("tv_minute")
                || lower.equals("colon1") || lower.equals("colon2")
                || lower.equals("colon_view") || lower.contains("time_colon")
                || lower.contains("time_separator")
                || lower.endsWith("_hour") || lower.endsWith("_minute");
    }

    private static String resourceEntryName(View view) {
        if (view == null || view.getId() == View.NO_ID) return null;
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int[] findAlphaBounds(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) {
                if ((row[x] >>> 24) == 0) continue;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
        if (maxX < minX || maxY < minY) return null;
        // One-pixel guard keeps antialiased edge samples complete.
        minX = Math.max(0, minX - 1);
        minY = Math.max(0, minY - 1);
        maxX = Math.min(width - 1, maxX + 1);
        maxY = Math.min(height - 1, maxY + 1);
        return new int[]{minX, minY, maxX + 1, maxY + 1};
    }

    private static long mix(long hash, int value) {
        hash ^= value;
        return hash * 1099511628211L;
    }
}
