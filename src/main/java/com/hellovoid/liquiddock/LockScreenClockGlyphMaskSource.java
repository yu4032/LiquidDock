package com.hellovoid.liquiddock;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * Stable local-space SDF source for native lockscreen time glyphs.
 *
 * <p>Glyph pixels are rasterized in each View's own local coordinates. View/parent transforms are
 * carried only by {@link GlyphFrame#maskToRoot}, so scale/translation animation never regenerates
 * or uploads the SDF texture. Content is regenerated only when semantic text/font/layout state
 * changes.</p>
 */
final class LockScreenClockGlyphMaskSource {
    private static final int MASK_GUARD_PX = 28;
    private static final float SDF_RANGE_PX = 24f;
    private static final float LARGE_DISTANCE = 1.0e12f;

    static final class GlyphFrame {
        final int slot;
        final Bitmap bitmap; // Non-null only when this slot's content signature changed.
        final long contentSignature;
        final int rootWidth;
        final int rootHeight;
        final float[] maskToRoot;
        final float sdfRangeRootPx;
        final RectF rootBounds;

        GlyphFrame(
                int slot,
                Bitmap bitmap,
                long contentSignature,
                int rootWidth,
                int rootHeight,
                float[] maskToRoot,
                float sdfRangeRootPx,
                RectF rootBounds) {
            this.slot = slot;
            this.bitmap = bitmap;
            this.contentSignature = contentSignature;
            this.rootWidth = rootWidth;
            this.rootHeight = rootHeight;
            this.maskToRoot = maskToRoot;
            this.sdfRangeRootPx = sdfRangeRootPx;
            this.rootBounds = rootBounds;
        }
    }

    static final class Frame {
        final int rootWidth;
        final int rootHeight;
        final ArrayList<GlyphFrame> glyphs;

        Frame(int rootWidth, int rootHeight, ArrayList<GlyphFrame> glyphs) {
            this.rootWidth = rootWidth;
            this.rootHeight = rootHeight;
            this.glyphs = glyphs;
        }

        boolean isEmpty() {
            return glyphs.isEmpty();
        }
    }

    private final View clockRoot;
    private final ArrayList<View> glyphViews;
    private final WeakHashMap<View, Float> originalAlpha = new WeakHashMap<>();
    private final WeakHashMap<View, Integer> originalVisibility = new WeakHashMap<>();
    private final WeakHashMap<View, Long> lastContentSignatures = new WeakHashMap<>();

    private LockScreenClockGlyphMaskSource(View clockRoot, List<View> glyphViews) {
        this.clockRoot = clockRoot;
        this.glyphViews = new ArrayList<>(glyphViews);
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
        View common = lowestCommonAncestor(roots);
        if (common == null) common = roots.get(0).getRootView();
        if (common == null) return null;
        return new LockScreenClockGlyphMaskSource(common, glyphs);
    }

    Frame captureFrame() {
        View windowRoot = clockRoot.getRootView();
        if (windowRoot == null || !windowRoot.isAttachedToWindow()
                || windowRoot.getWidth() <= 0 || windowRoot.getHeight() <= 0) return null;

        Matrix rootToGlobal = new Matrix();
        windowRoot.transformMatrixToGlobal(rootToGlobal);
        Matrix globalToRoot = new Matrix();
        if (!rootToGlobal.invert(globalToRoot)) return null;

        ArrayList<GlyphFrame> out = new ArrayList<>();
        for (int slot = 0; slot < glyphViews.size(); slot++) {
            View glyph = glyphViews.get(slot);
            if (glyph == null || !glyph.isAttachedToWindow()
                    || glyph.getWidth() <= 0 || glyph.getHeight() <= 0) continue;

            Integer storedVisibility = originalVisibility.get(glyph);
            int effectiveVisibility = storedVisibility != null
                    ? storedVisibility : glyph.getVisibility();
            if (effectiveVisibility != View.VISIBLE) continue;

            long signature = contentSignature(glyph);
            Long previousSignature = lastContentSignatures.get(glyph);
            Bitmap sdf = null;
            if (previousSignature == null || previousSignature.longValue() != signature) {
                sdf = rasterizeStableLocalSdf(glyph);
                if (sdf == null) continue;
                lastContentSignatures.put(glyph, signature);
            }

            Matrix glyphToGlobal = new Matrix();
            glyph.transformMatrixToGlobal(glyphToGlobal);
            Matrix glyphToRoot = new Matrix();
            glyphToRoot.setConcat(globalToRoot, glyphToGlobal);

            int maskWidth = glyph.getWidth() + MASK_GUARD_PX * 2;
            int maskHeight = glyph.getHeight() + MASK_GUARD_PX * 2;
            Matrix maskToGlyph = new Matrix();
            maskToGlyph.setScale(maskWidth, maskHeight);
            maskToGlyph.postTranslate(-MASK_GUARD_PX, -MASK_GUARD_PX);
            Matrix maskToRoot = new Matrix();
            maskToRoot.setConcat(glyphToRoot, maskToGlyph);
            float[] values = new float[9];
            maskToRoot.getValues(values);

            RectF rootBounds = transformedUnitBounds(values);
            float rootScale = matrixAreaScale(glyphToRoot);
            float sdfRangeRoot = Math.max(1f, SDF_RANGE_PX * rootScale);

            out.add(new GlyphFrame(
                    slot,
                    sdf,
                    signature,
                    windowRoot.getWidth(),
                    windowRoot.getHeight(),
                    values,
                    sdfRangeRoot,
                    rootBounds));
        }
        return out.isEmpty() ? null
                : new Frame(windowRoot.getWidth(), windowRoot.getHeight(), out);
    }

    boolean hasGlyphs() {
        return !glyphViews.isEmpty();
    }

    int glyphCount() {
        return glyphViews.size();
    }

    void suppressNativeGlyphs() {
        for (View glyph : glyphViews) {
            if (glyph == null) continue;
            if (!originalAlpha.containsKey(glyph)) originalAlpha.put(glyph, glyph.getAlpha());
            if (!originalVisibility.containsKey(glyph)) {
                originalVisibility.put(glyph, glyph.getVisibility());
            }
            // Preserve measurement and all native clock animation/layout ownership.
            glyph.setVisibility(View.INVISIBLE);
        }
    }

    void restoreNativeGlyphs() {
        ArrayList<View> views = new ArrayList<>(originalVisibility.keySet());
        for (View glyph : views) {
            if (glyph == null) continue;
            Float alpha = originalAlpha.get(glyph);
            Integer visibility = originalVisibility.get(glyph);
            if (alpha != null) glyph.setAlpha(alpha);
            if (visibility != null) glyph.setVisibility(visibility);
        }
        originalVisibility.clear();
        originalAlpha.clear();
    }

    private Bitmap rasterizeStableLocalSdf(View glyph) {
        int width = glyph.getWidth();
        int height = glyph.getHeight();
        if (width <= 0 || height <= 0) return null;
        int bitmapWidth = width + MASK_GUARD_PX * 2;
        int bitmapHeight = height + MASK_GUARD_PX * 2;
        Bitmap alphaBitmap = Bitmap.createBitmap(
                bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(alphaBitmap);
        canvas.drawColor(Color.TRANSPARENT);
        canvas.translate(MASK_GUARD_PX, MASK_GUARD_PX);

        float currentAlpha = glyph.getAlpha();
        int currentVisibility = glyph.getVisibility();
        Float storedAlpha = originalAlpha.get(glyph);
        Integer storedVisibility = originalVisibility.get(glyph);
        float captureAlpha = storedAlpha != null ? storedAlpha : currentAlpha;
        int captureVisibility = storedVisibility != null ? storedVisibility : currentVisibility;
        if (captureAlpha <= 0f) captureAlpha = 1f;
        if (captureVisibility != View.VISIBLE) captureVisibility = View.VISIBLE;
        try {
            if (currentVisibility != captureVisibility) glyph.setVisibility(captureVisibility);
            if (currentAlpha != captureAlpha) glyph.setAlpha(captureAlpha);
            glyph.draw(canvas);
        } finally {
            if (glyph.getAlpha() != currentAlpha) glyph.setAlpha(currentAlpha);
            if (glyph.getVisibility() != currentVisibility) glyph.setVisibility(currentVisibility);
        }

        Bitmap sdf = toExactSignedDistanceBitmap(alphaBitmap, SDF_RANGE_PX);
        alphaBitmap.recycle();
        return sdf;
    }

    private static Bitmap toExactSignedDistanceBitmap(Bitmap alphaBitmap, float rangePx) {
        if (alphaBitmap == null || alphaBitmap.isRecycled()) return null;
        int width = alphaBitmap.getWidth();
        int height = alphaBitmap.getHeight();
        int count = width * height;
        if (count <= 0) return null;

        int[] pixels = new int[count];
        alphaBitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        boolean[] inside = new boolean[count];
        boolean anyInside = false;
        for (int i = 0; i < count; i++) {
            int alpha = (pixels[i] >>> 24) & 0xff;
            inside[i] = alpha >= 128;
            anyInside |= inside[i];
        }
        if (!anyInside) return null;

        float[] toInsideSquared = exactSquaredDistance(inside, width, height, true);
        float[] toOutsideSquared = exactSquaredDistance(inside, width, height, false);
        int[] encoded = new int[count];
        float safeRange = Math.max(1f, rangePx);
        for (int i = 0; i < count; i++) {
            int alpha = (pixels[i] >>> 24) & 0xff;
            float signed;
            if (alpha > 0 && alpha < 255) {
                // Preserve Android's antialiased glyph coverage as a sub-pixel boundary estimate.
                signed = 0.5f - (alpha / 255f);
            } else if (inside[i]) {
                signed = -Math.max(0f, (float) Math.sqrt(toOutsideSquared[i]) - 0.5f);
            } else {
                signed = Math.max(0f, (float) Math.sqrt(toInsideSquared[i]) - 0.5f);
            }
            signed = Math.max(-safeRange, Math.min(safeRange, signed));
            float normalized = 0.5f - signed / (2f * safeRange);
            int a = Math.max(0, Math.min(255, Math.round(normalized * 255f)));
            encoded[i] = (a << 24) | 0x00ffffff;
        }
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        out.setPixels(encoded, 0, width, 0, 0, width, height);
        return out;
    }

    /** Exact O(n) squared Euclidean distance transform (Felzenszwalb/Huttenlocher). */
    private static float[] exactSquaredDistance(
            boolean[] mask, int width, int height, boolean targetValue) {
        int max = Math.max(width, height);
        float[] f = new float[max];
        float[] d = new float[max];
        int[] v = new int[max];
        float[] z = new float[max + 1];
        float[] temp = new float[width * height];
        float[] out = new float[width * height];

        for (int y = 0; y < height; y++) {
            int base = y * width;
            for (int x = 0; x < width; x++) {
                f[x] = mask[base + x] == targetValue ? 0f : LARGE_DISTANCE;
            }
            distanceTransform1d(f, width, d, v, z);
            System.arraycopy(d, 0, temp, base, width);
        }
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) f[y] = temp[y * width + x];
            distanceTransform1d(f, height, d, v, z);
            for (int y = 0; y < height; y++) out[y * width + x] = d[y];
        }
        return out;
    }

    private static void distanceTransform1d(
            float[] f, int n, float[] d, int[] v, float[] z) {
        int k = 0;
        v[0] = 0;
        z[0] = Float.NEGATIVE_INFINITY;
        z[1] = Float.POSITIVE_INFINITY;
        for (int q = 1; q < n; q++) {
            float s;
            do {
                int vk = v[k];
                s = ((f[q] + q * q) - (f[vk] + vk * vk))
                        / (2f * (q - vk));
                if (s <= z[k]) k--;
            } while (s <= z[k] && k >= 0);
            if (k < 0) {
                k = 0;
                v[0] = q;
                z[0] = Float.NEGATIVE_INFINITY;
                z[1] = Float.POSITIVE_INFINITY;
            } else {
                k++;
                v[k] = q;
                z[k] = s;
                z[k + 1] = Float.POSITIVE_INFINITY;
            }
        }
        k = 0;
        for (int q = 0; q < n; q++) {
            while (z[k + 1] < q) k++;
            float delta = q - v[k];
            d[q] = delta * delta + f[v[k]];
        }
    }

    private static long contentSignature(View glyph) {
        long hash = 1469598103934665603L;
        hash = mix(hash, glyph.getClass().getName().hashCode());
        hash = mix(hash, glyph.getWidth());
        hash = mix(hash, glyph.getHeight());
        hash = mix(hash, glyph.getPaddingLeft());
        hash = mix(hash, glyph.getPaddingTop());
        hash = mix(hash, glyph.getPaddingRight());
        hash = mix(hash, glyph.getPaddingBottom());
        if (glyph instanceof TextView) {
            TextView textView = (TextView) glyph;
            CharSequence text = textView.getText();
            Paint paint = textView.getPaint();
            hash = mix(hash, text == null ? 0 : text.toString().hashCode());
            hash = mix(hash, Float.floatToIntBits(textView.getTextSize()));
            hash = mix(hash, Float.floatToIntBits(textView.getLetterSpacing()));
            hash = mix(hash, Float.floatToIntBits(paint.getTextScaleX()));
            hash = mix(hash, Float.floatToIntBits(paint.getTextSkewX()));
            hash = mix(hash, paint.getFlags());
            hash = mix(hash, paint.isFakeBoldText() ? 1 : 0);
            hash = mix(hash, System.identityHashCode(textView.getTypeface()));
            hash = mix(hash, textView.getGravity());
        }
        return hash;
    }

    private static RectF transformedUnitBounds(float[] m) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float[][] p = {{0f,0f},{1f,0f},{0f,1f},{1f,1f}};
        for (float[] v : p) {
            float x = m[0] * v[0] + m[1] * v[1] + m[2];
            float y = m[3] * v[0] + m[4] * v[1] + m[5];
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        return new RectF(minX, minY, maxX, maxY);
    }

    private static float matrixAreaScale(Matrix matrix) {
        float[] m = new float[9];
        matrix.getValues(m);
        float determinant = m[0] * m[4] - m[1] * m[3];
        float scale = (float) Math.sqrt(Math.max(1.0e-6f, Math.abs(determinant)));
        return Float.isFinite(scale) ? scale : 1f;
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
        String lower = resourceName == null ? "" : resourceName.toLowerCase(Locale.ROOT);
        boolean excluded = isExcludedClockMetadataName(lower);
        boolean timeContainer = !excluded && (insideTimeContainer
                || lower.equals("time_container")
                || lower.equals("hour_container")
                || lower.equals("minute_container")
                || lower.contains("clock_time")
                || lower.contains("time_content")
                || lower.contains("time_group"));
        boolean semanticTimeId = isTimeResourceName(resourceName);

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
        String lower = name.toLowerCase(Locale.ROOT);
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

    private static long mix(long hash, int value) {
        hash ^= value;
        return hash * 1099511628211L;
    }
}
