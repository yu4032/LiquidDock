package com.hellovoid.liquiddock;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
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
        final int rootWidth;
        final int rootHeight;
        final long signature;
        /** Android Matrix values mapping normalized mask coordinates into root pixel coordinates. */
        final float[] maskToRoot;

        Mask(Bitmap bitmap, int rootWidth, int rootHeight, long signature, float[] maskToRoot) {
            this.bitmap = bitmap;
            this.rootWidth = rootWidth;
            this.rootHeight = rootHeight;
            this.signature = signature;
            this.maskToRoot = maskToRoot;
        }
    }

    private final View clockRoot;
    private final List<View> glyphViews;
    private final WeakHashMap<View, Float> originalAlpha = new WeakHashMap<>();
    private final WeakHashMap<View, Integer> originalVisibility = new WeakHashMap<>();
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
        collectSemanticTimeViews(clockRoot, glyphs, false);
        if (glyphs.isEmpty()) return null;
        return new LockScreenClockGlyphMaskSource(clockRoot, glyphs);
    }

    Mask capture() {
        View windowRoot = clockRoot.getRootView();
        if (windowRoot == null || !windowRoot.isAttachedToWindow()
                || windowRoot.getWidth() <= 0 || windowRoot.getHeight() <= 0
                || clockRoot.getWidth() <= 0 || clockRoot.getHeight() <= 0) return null;

        // Keep the mask in clock-local coordinates. Its bitmap size is therefore stable while the
        // native clock animates; only maskToRoot changes frame-to-frame.
        Bitmap bitmap = Bitmap.createBitmap(
                clockRoot.getWidth(), clockRoot.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT);

        Matrix clockToGlobal = new Matrix();
        clockRoot.transformMatrixToGlobal(clockToGlobal);
        Matrix globalToClock = new Matrix();
        if (!clockToGlobal.invert(globalToClock)) {
            bitmap.recycle();
            return null;
        }

        long signature = 1469598103934665603L;
        int drawn = 0;
        for (View glyph : glyphViews) {
            if (glyph == null || !glyph.isAttachedToWindow()
                    || glyph.getWidth() <= 0 || glyph.getHeight() <= 0) continue;
            Integer nativeVisibility = originalVisibility.get(glyph);
            int effectiveVisibility = nativeVisibility != null
                    ? nativeVisibility : glyph.getVisibility();
            if (effectiveVisibility != View.VISIBLE) continue;

            Matrix glyphToGlobal = new Matrix();
            glyph.transformMatrixToGlobal(glyphToGlobal);
            Matrix glyphToClock = new Matrix();
            glyphToClock.setConcat(globalToClock, glyphToGlobal);

            int save = canvas.save();
            canvas.concat(glyphToClock);
            float currentAlpha = glyph.getAlpha();
            int currentVisibility = glyph.getVisibility();
            Float nativeAlpha = originalAlpha.get(glyph);
            Integer storedVisibility = originalVisibility.get(glyph);
            float captureAlpha = nativeAlpha != null ? nativeAlpha : currentAlpha;
            int captureVisibility = storedVisibility != null ? storedVisibility : currentVisibility;
            if (captureAlpha <= 0f) captureAlpha = 1f;
            if (captureVisibility != View.VISIBLE) captureVisibility = View.VISIBLE;
            if (currentVisibility != captureVisibility) glyph.setVisibility(captureVisibility);
            if (currentAlpha != captureAlpha) glyph.setAlpha(captureAlpha);
            try {
                glyph.draw(canvas);
                drawn++;
            } finally {
                if (glyph.getAlpha() != currentAlpha) glyph.setAlpha(currentAlpha);
                if (glyph.getVisibility() != currentVisibility) glyph.setVisibility(currentVisibility);
                canvas.restoreToCount(save);
            }

            signature = mix(signature, glyph.getWidth());
            signature = mix(signature, glyph.getHeight());
            if (glyph instanceof TextView) {
                CharSequence text = ((TextView) glyph).getText();
                signature = mix(signature, text == null ? 0 : text.toString().hashCode());
            }
        }

        if (drawn == 0) {
            bitmap.recycle();
            return null;
        }

        int[] tight = findAlphaBounds(bitmap);
        if (tight == null) {
            bitmap.recycle();
            return null;
        }
        int left = tight[0];
        int top = tight[1];
        int right = tight[2];
        int bottom = tight[3];
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) {
            bitmap.recycle();
            return null;
        }

        Bitmap tightBitmap = Bitmap.createBitmap(bitmap, left, top, width, height);
        bitmap.recycle();

        // Map normalized tight-mask coordinates -> clock local pixels -> window-root pixels.
        Matrix maskToClock = new Matrix();
        maskToClock.setScale(width, height);
        maskToClock.postTranslate(left, top);

        Matrix rootToGlobal = new Matrix();
        windowRoot.transformMatrixToGlobal(rootToGlobal);
        Matrix globalToRoot = new Matrix();
        if (!rootToGlobal.invert(globalToRoot)) {
            tightBitmap.recycle();
            return null;
        }
        Matrix clockToRoot = new Matrix();
        clockToRoot.setConcat(globalToRoot, clockToGlobal);
        Matrix maskToRootMatrix = new Matrix();
        maskToRootMatrix.setConcat(clockToRoot, maskToClock);
        float[] values = new float[9];
        maskToRootMatrix.getValues(values);

        long bitmapSignature = mix(mix(mix(mix(signature, left), top), width), height);
        // Bitmap content only changes when text/layout changes; transform animation is carried by
        // maskToRoot and intentionally excluded from the upload signature.
        if (bitmapSignature == lastSignature) {
            // Preserve transform updates without forcing a texture upload. Return the bitmap as a
            // disposable carrier; the session will ignore duplicate texture content.
        } else {
            lastSignature = bitmapSignature;
        }
        return new Mask(tightBitmap, windowRoot.getWidth(), windowRoot.getHeight(),
                bitmapSignature, values);
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

    private static void collectSemanticTimeViews(
            View root, List<View> out, boolean insideTimeContainer) {
        String resourceName = resourceEntryName(root);
        String lower = resourceName == null
                ? "" : resourceName.toLowerCase(java.util.Locale.ROOT);
        boolean excluded = isExcludedClockMetadataName(lower);
        boolean timeContainer = !excluded && (insideTimeContainer
                || lower.equals("time_container")
                || lower.equals("hour_container")
                || lower.equals("minute_container")
                || lower.contains("clock_time")
                || lower.contains("time_content")
                || lower.contains("time_group"));
        boolean semanticTimeId = isTimeResourceName(resourceName);

        // A text node is authoritative when its own resource name identifies time/hour/minute,
        // or when it belongs to an explicitly named time/hour/minute container. This catches
        // split digits and separators without pulling date/weather metadata into the mask.
        if (root instanceof TextView && !excluded
                && (semanticTimeId || timeContainer) && !out.contains(root)) {
            out.add(root);
        }

        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectSemanticTimeViews(group.getChildAt(i), out, timeContainer);
            }
        }
    }

    private static boolean isExcludedClockMetadataName(String lower) {
        if (lower == null || lower.isEmpty()) return false;
        return lower.contains("date") || lower.contains("week")
                || lower.contains("weather") || lower.contains("signature")
                || lower.contains("notification") || lower.contains("lunar");
    }

    private static boolean isTimeResourceName(String name) {
        if (name == null || name.isEmpty()) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (isExcludedClockMetadataName(lower)) return false;
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
