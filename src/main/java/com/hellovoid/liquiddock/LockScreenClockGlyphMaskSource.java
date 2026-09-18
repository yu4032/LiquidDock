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
    private long lastSignature = Long.MIN_VALUE;

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
                    || glyph.getWidth() <= 0 || glyph.getHeight() <= 0
                    || glyph.getVisibility() != View.VISIBLE) continue;
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
        signature = mix(signature, left);
        signature = mix(signature, top);
        signature = mix(signature, right);
        signature = mix(signature, bottom);
        if (signature == lastSignature) return null;
        lastSignature = signature;

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
            Float nativeAlpha = originalAlpha.get(glyph);
            float captureAlpha = nativeAlpha != null ? nativeAlpha : currentAlpha;
            if (captureAlpha <= 0f) captureAlpha = 1f;
            if (currentAlpha != captureAlpha) glyph.setAlpha(captureAlpha);
            try {
                glyph.draw(canvas);
            } finally {
                if (glyph.getAlpha() != currentAlpha) glyph.setAlpha(currentAlpha);
                canvas.restoreToCount(save);
            }
        }
        return new Mask(bitmap, left, top, width, height,
                windowRoot.getWidth(), windowRoot.getHeight(), signature);
    }

    void suppressNativeGlyphs() {
        for (View glyph : glyphViews) {
            if (glyph == null) continue;
            if (!originalAlpha.containsKey(glyph)) originalAlpha.put(glyph, glyph.getAlpha());
            glyph.setAlpha(0f);
        }
    }

    void restoreNativeGlyphs() {
        for (View glyph : new ArrayList<>(originalAlpha.keySet())) {
            Float alpha = originalAlpha.get(glyph);
            if (glyph != null && alpha != null) glyph.setAlpha(alpha);
        }
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

    private static long mix(long hash, int value) {
        hash ^= value;
        return hash * 1099511628211L;
    }
}
