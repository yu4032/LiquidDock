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
        final float sdfRangePx;
        /** Android Matrix values mapping normalized mask coordinates into root pixel coordinates. */
        final float[] maskToRoot;

        Mask(Bitmap bitmap, int rootWidth, int rootHeight, long signature,
             float sdfRangePx, float[] maskToRoot) {
            this.bitmap = bitmap;
            this.rootWidth = rootWidth;
            this.rootHeight = rootHeight;
            this.signature = signature;
            this.sdfRangePx = sdfRangePx;
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
        ArrayList<View> roots = new ArrayList<>();
        roots.add(clockRoot);
        return resolve(roots);
    }

    static LockScreenClockGlyphMaskSource resolve(List<View> clockRoots) {
        if (clockRoots == null || clockRoots.isEmpty()) return null;
        ArrayList<View> roots = new ArrayList<>();
        ArrayList<View> glyphs = new ArrayList<>();
        for (View clockRoot : clockRoots) {
            if (clockRoot == null) continue;
            roots.add(clockRoot);
            addSemanticField(clockRoot, "mTimeView", glyphs);
            addSemanticField(clockRoot, "mTimeView2", glyphs);
            addSemanticField(clockRoot, "mHourTextStyle1", glyphs);
            addSemanticField(clockRoot, "mHourTextStyle2", glyphs);
            addSemanticField(clockRoot, "mMinuteTextStyle1", glyphs);
            addSemanticField(clockRoot, "mMinuteTextStyle2", glyphs);
            collectSemanticTimeViews(clockRoot, glyphs, false);
        }
        if (roots.isEmpty() || glyphs.isEmpty()) return null;
        View host = lowestCommonAncestor(roots);
        if (host == null) host = roots.get(0).getRootView();
        if (host == null) return null;
        return new LockScreenClockGlyphMaskSource(host, glyphs);
    }

    private static View lowestCommonAncestor(List<View> roots) {
        if (roots == null || roots.isEmpty()) return null;
        ArrayList<View> chain = new ArrayList<>();
        View current = roots.get(0);
        while (current != null) {
            chain.add(current);
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        for (View candidate : chain) {
            boolean common = true;
            for (int i = 1; i < roots.size(); i++) {
                if (!isAncestor(candidate, roots.get(i))) {
                    common = false;
                    break;
                }
            }
            if (common) return candidate;
        }
        return null;
    }

    private static boolean isAncestor(View ancestor, View view) {
        View current = view;
        while (current != null) {
            if (current == ancestor) return true;
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
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
            float[] glyphLocalMatrix = new float[9];
            glyphToClock.getValues(glyphLocalMatrix);

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
            for (float value : glyphLocalMatrix) {
                signature = mix(signature, Float.floatToIntBits(value));
            }
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
        final float sdfRangePx = 32f;
        Bitmap sdfBitmap = toSignedDistanceBitmap(tightBitmap, sdfRangePx);
        tightBitmap.recycle();
        if (sdfBitmap == null) return null;
        return new Mask(sdfBitmap, windowRoot.getWidth(), windowRoot.getHeight(),
                bitmapSignature, sdfRangePx, values);
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

    private static Bitmap toSignedDistanceBitmap(Bitmap alphaBitmap, float rangePx) {
        if (alphaBitmap == null || alphaBitmap.isRecycled()) return null;
        int width = alphaBitmap.getWidth();
        int height = alphaBitmap.getHeight();
        int count = width * height;
        if (count <= 0) return null;

        int[] pixels = new int[count];
        alphaBitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        boolean[] inside = new boolean[count];
        for (int i = 0; i < count; i++) inside[i] = ((pixels[i] >>> 24) & 0xff) >= 96;

        float[] toInside = chamferDistance(inside, width, height, true);
        float[] toOutside = chamferDistance(inside, width, height, false);
        int[] sdf = new int[count];
        float safeRange = Math.max(1f, rangePx);
        for (int i = 0; i < count; i++) {
            float signed = inside[i] ? -toOutside[i] : toInside[i];
            signed = Math.max(-safeRange, Math.min(safeRange, signed));
            float encoded = 0.5f - signed / (2f * safeRange);
            int a = Math.max(0, Math.min(255, Math.round(encoded * 255f)));
            sdf[i] = (a << 24) | 0x00ffffff;
        }
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        out.setPixels(sdf, 0, width, 0, 0, width, height);
        return out;
    }

    private static float[] chamferDistance(
            boolean[] inside, int width, int height, boolean targetInside) {
        final float inf = 1_000_000f;
        final float diag = 1.41421356f;
        int count = width * height;
        float[] d = new float[count];
        for (int i = 0; i < count; i++) d[i] = inside[i] == targetInside ? 0f : inf;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                float v = d[i];
                if (x > 0) v = Math.min(v, d[i - 1] + 1f);
                if (y > 0) v = Math.min(v, d[i - width] + 1f);
                if (x > 0 && y > 0) v = Math.min(v, d[i - width - 1] + diag);
                if (x + 1 < width && y > 0) v = Math.min(v, d[i - width + 1] + diag);
                d[i] = v;
            }
        }
        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                int i = y * width + x;
                float v = d[i];
                if (x + 1 < width) v = Math.min(v, d[i + 1] + 1f);
                if (y + 1 < height) v = Math.min(v, d[i + width] + 1f);
                if (x + 1 < width && y + 1 < height) v = Math.min(v, d[i + width + 1] + diag);
                if (x > 0 && y + 1 < height) v = Math.min(v, d[i + width - 1] + diag);
                d[i] = v;
            }
        }
        return d;
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
