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

/** Applies optional white text/icon styling to shortcut-menu content without vendor listener replacement. */
final class ShortcutMenuTextColorController {
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

    private ShortcutMenuTextColorController() {}

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

    private static synchronized void detach(View root) {
        ViewTreeObserver.OnGlobalLayoutListener listener = LISTENERS.remove(root);
        if (listener == null) return;
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnGlobalLayoutListener(listener);
        }
    }

    private static void applyDarkMode(View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setTextColor(Color.WHITE);
            textView.setCompoundDrawableTintList(WHITE_TINT);
        }
        if (view instanceof ImageView) {
            ImageView imageView = (ImageView) view;
            Drawable drawable = imageView.getDrawable();
            if (shouldTintIcon(drawable)) {
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
