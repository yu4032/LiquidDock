package com.hellovoid.liquiddock;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.WeakHashMap;

/** Applies shortcut-menu dark-mode content styling without replacing vendor listeners. */
final class ShortcutMenuDarkModeController {
    private static final ColorStateList WHITE_TINT = ColorStateList.valueOf(Color.WHITE);
    private static final int SAMPLE_SIZE_PX = 20;
    private static final int MIN_VISIBLE_ALPHA = 32;
    private static final int MAX_NEUTRAL_CHANNEL_SPREAD = 36;
    private static final int MAX_DARK_CHANNEL = 128;
    private static final float MIN_NEUTRAL_FRACTION = 0.88f;
    private static final float MIN_DARK_FRACTION = 0.72f;

    private static final WeakHashMap<View, ViewTreeObserver.OnGlobalLayoutListener> LISTENERS =
            new WeakHashMap<>();
    private static final WeakHashMap<Drawable, Boolean> ICON_TINT_CACHE = new WeakHashMap<>();
    // Restore each view's actual vendor style on a live toggle rather than hardcoding black.
    private static final WeakHashMap<View, ColorStateList> ORIGINAL_TEXT_COLORS = new WeakHashMap<>();
    private static final WeakHashMap<TextView, ColorStateList> ORIGINAL_COMPOUND_TINTS =
            new WeakHashMap<>();
    private static final WeakHashMap<ImageView, ColorStateList> ORIGINAL_IMAGE_TINTS =
            new WeakHashMap<>();

    private ShortcutMenuDarkModeController() {}

    static synchronized void attach(View root) {
        if (root == null) return;
        applyDarkMode(root);
        if (LISTENERS.containsKey(root)) return;

        ViewTreeObserver.OnGlobalLayoutListener listener = () -> applyDarkMode(root);
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (!observer.isAlive()) return;
        observer.addOnGlobalLayoutListener(listener);
        LISTENERS.put(root, listener);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}

            @Override public void onViewDetachedFromWindow(View v) {
                detach(v);
                v.removeOnAttachStateChangeListener(this);
            }
        });
    }

    static synchronized void detach(View root) {
        if (root == null) return;
        ViewTreeObserver.OnGlobalLayoutListener listener = LISTENERS.remove(root);
        if (listener != null) {
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
        }
        restoreVendorColors(root);
    }

    private static void restoreVendorColors(View view) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            ColorStateList text = ORIGINAL_TEXT_COLORS.remove(tv);
            if (text != null) tv.setTextColor(text);
            if (ORIGINAL_COMPOUND_TINTS.containsKey(tv)) {
                tv.setCompoundDrawableTintList(ORIGINAL_COMPOUND_TINTS.remove(tv));
            }
        }
        if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            if (ORIGINAL_IMAGE_TINTS.containsKey(image)) {
                image.setImageTintList(ORIGINAL_IMAGE_TINTS.remove(image));
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                restoreVendorColors(group.getChildAt(i));
            }
        }
    }

    private static void applyDarkMode(View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if (!ORIGINAL_TEXT_COLORS.containsKey(textView)) {
                ORIGINAL_TEXT_COLORS.put(textView, textView.getTextColors());
                ORIGINAL_COMPOUND_TINTS.put(
                        textView, textView.getCompoundDrawableTintList());
            }
            textView.setTextColor(Color.WHITE);
            textView.setCompoundDrawableTintList(WHITE_TINT);
        }
        if (view instanceof ImageView) {
            ImageView imageView = (ImageView) view;
            Drawable drawable = imageView.getDrawable();
            if (shouldTintIcon(drawable)) {
                if (!ORIGINAL_IMAGE_TINTS.containsKey(imageView)) {
                    ORIGINAL_IMAGE_TINTS.put(imageView, imageView.getImageTintList());
                }
                imageView.setImageTintList(WHITE_TINT);
            }
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            applyDarkMode(group.getChildAt(i));
        }
    }

    private static boolean shouldTintIcon(Drawable drawable) {
        if (drawable == null) return false;
        synchronized (ICON_TINT_CACHE) {
            Boolean cached = ICON_TINT_CACHE.get(drawable);
            if (cached != null) return cached;
        }

        boolean result = scanNearBlackMonochrome(drawable);
        synchronized (ICON_TINT_CACHE) {
            ICON_TINT_CACHE.put(drawable, result);
        }
        return result;
    }

    private static boolean scanNearBlackMonochrome(Drawable drawable) {
        Bitmap bitmap = null;
        Rect originalBounds = new Rect(drawable.getBounds());
        try {
            bitmap = Bitmap.createBitmap(SAMPLE_SIZE_PX, SAMPLE_SIZE_PX, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, SAMPLE_SIZE_PX, SAMPLE_SIZE_PX);
            drawable.draw(canvas);

            int visible = 0;
            int neutral = 0;
            int dark = 0;
            for (int y = 0; y < SAMPLE_SIZE_PX; y++) {
                for (int x = 0; x < SAMPLE_SIZE_PX; x++) {
                    int color = bitmap.getPixel(x, y);
                    if (Color.alpha(color) < MIN_VISIBLE_ALPHA) continue;
                    visible++;

                    int red = Color.red(color);
                    int green = Color.green(color);
                    int blue = Color.blue(color);
                    int max = Math.max(red, Math.max(green, blue));
                    int min = Math.min(red, Math.min(green, blue));
                    if (max - min <= MAX_NEUTRAL_CHANNEL_SPREAD) neutral++;
                    if (max <= MAX_DARK_CHANNEL) dark++;
                }
            }

            if (visible == 0) return false;
            float neutralFraction = neutral / (float) visible;
            float darkFraction = dark / (float) visible;
            return neutralFraction >= MIN_NEUTRAL_FRACTION && darkFraction >= MIN_DARK_FRACTION;
        } catch (Throwable ignored) {
            return false;
        } finally {
            drawable.setBounds(originalBounds);
            if (bitmap != null) bitmap.recycle();
        }
    }
}
