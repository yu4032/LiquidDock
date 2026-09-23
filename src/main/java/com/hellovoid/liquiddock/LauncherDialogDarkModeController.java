package com.hellovoid.liquiddock;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Color-aware dark treatment for the exact Launcher dialog panel.
 *
 * <p>Only neutral dark content is promoted to light content. Chromatic semantic colors (for
 * example destructive red) and colorful icons are preserved. Button backgrounds are adapted by
 * installing a tinted clone of the vendor Drawable, never by mutating the original object.</p>
 */
final class LauncherDialogDarkModeController {
    private static final int PRIMARY_TEXT = 0xFFFFFFFF;
    private static final int BODY_TEXT = 0xE6FFFFFF;
    private static final int COMMENT_TEXT = 0xB8FFFFFF;
    private static final int DISABLED_TEXT = 0x61FFFFFF;

    private static final int SAMPLE_SIZE_PX = 20;
    private static final int MIN_VISIBLE_ALPHA = 32;
    private static final int MAX_NEUTRAL_CHANNEL_SPREAD = 36;
    private static final int MAX_DARK_CHANNEL = 128;
    private static final int MIN_LIGHT_CHANNEL = 196;
    private static final float MIN_NEUTRAL_FRACTION = 0.88f;
    private static final float MIN_DARK_FRACTION = 0.72f;
    private static final float MIN_LIGHT_FRACTION = 0.72f;

    private static final WeakHashMap<Drawable, Sample> DRAWABLE_SAMPLE_CACHE =
            new WeakHashMap<>();

    private LauncherDialogDarkModeController() {}

    static Session attach(View panel) {
        if (panel == null) return null;
        Session session = new Session(panel);
        session.reapply();
        return session;
    }

    static final class Session {
        private final View root;
        private final Map<TextView, TextSnapshot> textSnapshots = new IdentityHashMap<>();
        private final Map<ImageView, ImageSnapshot> imageSnapshots = new IdentityHashMap<>();
        private final Map<Button, ButtonSnapshot> buttonSnapshots = new IdentityHashMap<>();
        private boolean restored;

        Session(View root) {
            this.root = root;
        }

        void reapply() {
            if (restored || root == null) return;
            applyRecursive(root);
        }

        void restore() {
            if (restored) return;
            restored = true;

            for (Map.Entry<Button, ButtonSnapshot> entry
                    : new ArrayList<>(buttonSnapshots.entrySet())) {
                Button button = entry.getKey();
                ButtonSnapshot snapshot = entry.getValue();
                if (button == null || snapshot == null) continue;
                try {
                    button.setBackground(snapshot.originalBackground);
                    button.setBackgroundTintList(snapshot.originalBackgroundTint);
                } catch (Throwable ignored) {}
            }

            for (Map.Entry<ImageView, ImageSnapshot> entry
                    : new ArrayList<>(imageSnapshots.entrySet())) {
                ImageView image = entry.getKey();
                ImageSnapshot snapshot = entry.getValue();
                if (image == null || snapshot == null) continue;
                try {
                    image.setImageTintList(snapshot.originalTint);
                } catch (Throwable ignored) {}
            }

            for (Map.Entry<TextView, TextSnapshot> entry
                    : new ArrayList<>(textSnapshots.entrySet())) {
                TextView text = entry.getKey();
                TextSnapshot snapshot = entry.getValue();
                if (text == null || snapshot == null) continue;
                try {
                    text.setTextColor(snapshot.textColors);
                    text.setCompoundDrawableTintList(snapshot.compoundTint);
                } catch (Throwable ignored) {}
            }

            buttonSnapshots.clear();
            imageSnapshots.clear();
            textSnapshots.clear();
        }

        private void applyRecursive(View view) {
            if (view instanceof TextView) applyText((TextView) view);
            if (view instanceof ImageView) applyImage((ImageView) view);
            if (!(view instanceof ViewGroup)) return;
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyRecursive(group.getChildAt(i));
            }
        }

        private void applyText(TextView text) {
            TextSnapshot snapshot = textSnapshots.get(text);
            if (snapshot == null) {
                snapshot = new TextSnapshot(
                        text.getTextColors(),
                        text.getCompoundDrawableTintList(),
                        text.getCompoundDrawablesRelative());
                textSnapshots.put(text, snapshot);
            }

            if (text instanceof Button) {
                applyButton((Button) text, snapshot);
                return;
            }

            int original = snapshot.textColors.getDefaultColor();
            if (isNearBlackNeutral(original)) {
                String id = resourceEntryName(text).toLowerCase();
                int replacement;
                if (id.contains("title")) {
                    replacement = PRIMARY_TEXT;
                } else if (id.contains("comment") || id.contains("summary")) {
                    replacement = COMMENT_TEXT;
                } else {
                    replacement = BODY_TEXT;
                }
                text.setTextColor(textStates(replacement));
            } else {
                // Preserve destructive red, accent blue, and already-light vendor colors exactly.
                text.setTextColor(snapshot.textColors);
            }

            if (hasNearBlackMonochromeDrawable(snapshot.compoundDrawables)) {
                text.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
            } else {
                text.setCompoundDrawableTintList(snapshot.compoundTint);
            }
        }

        private void applyImage(ImageView image) {
            ImageSnapshot snapshot = imageSnapshots.get(image);
            if (snapshot == null) {
                snapshot = new ImageSnapshot(image.getImageTintList());
                imageSnapshots.put(image, snapshot);
            }

            Drawable drawable = image.getDrawable();
            if (drawable != null && sample(drawable).nearBlackMonochrome) {
                image.setImageTintList(ColorStateList.valueOf(Color.WHITE));
            } else {
                // App icons and other chromatic imagery must never be force-tinted.
                image.setImageTintList(snapshot.originalTint);
            }
        }

        private void applyButton(Button button, TextSnapshot textSnapshot) {
            ButtonSnapshot buttonSnapshot = buttonSnapshots.get(button);
            if (buttonSnapshot == null) {
                buttonSnapshot = new ButtonSnapshot(
                        button.getBackground(),
                        button.getBackgroundTintList());
                buttonSnapshots.put(button, buttonSnapshot);
            }

            int originalText = textSnapshot.textColors.getDefaultColor();
            boolean neutralText = isNearBlackNeutral(originalText);
            int adaptedText = neutralText ? Color.WHITE : liftChromaticForDark(originalText);
            button.setTextColor(neutralText
                    ? buttonTextStates(adaptedText)
                    : preserveAlphaStates(textSnapshot.textColors, adaptedText));

            if (hasNearBlackMonochromeDrawable(textSnapshot.compoundDrawables)) {
                button.setCompoundDrawableTintList(ColorStateList.valueOf(adaptedText));
            } else {
                button.setCompoundDrawableTintList(textSnapshot.compoundTint);
            }

            Drawable replacement = makeDarkButtonBackground(
                    button,
                    buttonSnapshot.originalBackground,
                    originalText,
                    "button1".equals(resourceEntryName(button)));
            if (replacement != null) {
                button.setBackgroundTintList(null);
                if (button.getBackground() != replacement) button.setBackground(replacement);
                buttonSnapshot.replacementBackground = replacement;
            } else {
                button.setBackground(buttonSnapshot.originalBackground);
                button.setBackgroundTintList(buttonSnapshot.originalBackgroundTint);
            }
        }
    }

    private static final class TextSnapshot {
        final ColorStateList textColors;
        final ColorStateList compoundTint;
        final Drawable[] compoundDrawables;

        TextSnapshot(
                ColorStateList textColors,
                ColorStateList compoundTint,
                Drawable[] compoundDrawables) {
            this.textColors = textColors;
            this.compoundTint = compoundTint;
            this.compoundDrawables = compoundDrawables != null
                    ? compoundDrawables.clone() : new Drawable[0];
        }
    }

    private static final class ImageSnapshot {
        final ColorStateList originalTint;

        ImageSnapshot(ColorStateList originalTint) {
            this.originalTint = originalTint;
        }
    }

    private static final class ButtonSnapshot {
        final Drawable originalBackground;
        final ColorStateList originalBackgroundTint;
        Drawable replacementBackground;

        ButtonSnapshot(Drawable originalBackground, ColorStateList originalBackgroundTint) {
            this.originalBackground = originalBackground;
            this.originalBackgroundTint = originalBackgroundTint;
        }
    }

    private static final class Sample {
        final boolean nearBlackMonochrome;
        final boolean nearLightMonochrome;
        final int averageColor;

        Sample(boolean nearBlackMonochrome, boolean nearLightMonochrome, int averageColor) {
            this.nearBlackMonochrome = nearBlackMonochrome;
            this.nearLightMonochrome = nearLightMonochrome;
            this.averageColor = averageColor;
        }
    }

    private static Drawable makeDarkButtonBackground(
            Button owner,
            Drawable original,
            int originalText,
            boolean primary) {
        if (owner == null || original == null) return null;
        Drawable.ConstantState state = original.getConstantState();
        if (state == null) return null;

        Sample background = sample(original);
        int base;
        if (!isNearBlackNeutral(originalText)) {
            // Destructive/accent buttons keep their semantic hue; only the surface becomes darker.
            base = liftChromaticForDark(originalText);
        } else if (background.nearLightMonochrome || background.nearBlackMonochrome) {
            base = Color.WHITE;
        } else {
            // A genuinely chromatic vendor background already carries semantics. Preserve its hue.
            base = liftChromaticForDark(background.averageColor);
        }

        try {
            Drawable clone = state.newDrawable(
                    owner.getResources(), owner.getContext().getTheme()).mutate();
            clone.setTintList(buttonBackgroundStates(base, primary));
            return clone;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean hasNearBlackMonochromeDrawable(Drawable[] drawables) {
        if (drawables == null) return false;
        boolean sawDrawable = false;
        for (Drawable drawable : drawables) {
            if (drawable == null) continue;
            sawDrawable = true;
            if (!sample(drawable).nearBlackMonochrome) return false;
        }
        return sawDrawable;
    }

    private static boolean isNearBlackNeutral(int color) {
        if (Color.alpha(color) < MIN_VISIBLE_ALPHA) return false;
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        return max - min <= MAX_NEUTRAL_CHANNEL_SPREAD && max <= MAX_DARK_CHANNEL;
    }

    private static Sample sample(Drawable drawable) {
        if (drawable == null) return new Sample(false, false, Color.TRANSPARENT);
        synchronized (DRAWABLE_SAMPLE_CACHE) {
            Sample cached = DRAWABLE_SAMPLE_CACHE.get(drawable);
            if (cached != null) return cached;
        }

        Sample result = scanDrawable(drawable);
        synchronized (DRAWABLE_SAMPLE_CACHE) {
            DRAWABLE_SAMPLE_CACHE.put(drawable, result);
        }
        return result;
    }

    private static Sample scanDrawable(Drawable drawable) {
        Bitmap bitmap = null;
        Rect originalBounds = new Rect(drawable.getBounds());
        try {
            bitmap = Bitmap.createBitmap(
                    SAMPLE_SIZE_PX, SAMPLE_SIZE_PX, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, SAMPLE_SIZE_PX, SAMPLE_SIZE_PX);
            drawable.draw(canvas);

            int visible = 0;
            int neutral = 0;
            int dark = 0;
            int light = 0;
            long sumR = 0;
            long sumG = 0;
            long sumB = 0;
            for (int y = 0; y < SAMPLE_SIZE_PX; y++) {
                for (int x = 0; x < SAMPLE_SIZE_PX; x++) {
                    int color = bitmap.getPixel(x, y);
                    if (Color.alpha(color) < MIN_VISIBLE_ALPHA) continue;
                    visible++;

                    int r = Color.red(color);
                    int g = Color.green(color);
                    int b = Color.blue(color);
                    sumR += r;
                    sumG += g;
                    sumB += b;

                    int max = Math.max(r, Math.max(g, b));
                    int min = Math.min(r, Math.min(g, b));
                    if (max - min <= MAX_NEUTRAL_CHANNEL_SPREAD) neutral++;
                    if (max <= MAX_DARK_CHANNEL) dark++;
                    if (min >= MIN_LIGHT_CHANNEL) light++;
                }
            }

            if (visible == 0) return new Sample(false, false, Color.TRANSPARENT);
            float neutralFraction = neutral / (float) visible;
            float darkFraction = dark / (float) visible;
            float lightFraction = light / (float) visible;
            int average = Color.rgb(
                    (int) (sumR / visible),
                    (int) (sumG / visible),
                    (int) (sumB / visible));
            return new Sample(
                    neutralFraction >= MIN_NEUTRAL_FRACTION
                            && darkFraction >= MIN_DARK_FRACTION,
                    neutralFraction >= MIN_NEUTRAL_FRACTION
                            && lightFraction >= MIN_LIGHT_FRACTION,
                    average);
        } catch (Throwable ignored) {
            return new Sample(false, false, Color.TRANSPARENT);
        } finally {
            drawable.setBounds(originalBounds);
            if (bitmap != null) bitmap.recycle();
        }
    }

    private static int liftChromaticForDark(int original) {
        if (isNearBlackNeutral(original)) return Color.WHITE;
        float[] hsv = new float[3];
        Color.colorToHSV(original, hsv);
        if (hsv[1] < 0.18f) return original;
        hsv[1] = Math.min(hsv[1], 0.82f);
        hsv[2] = Math.max(hsv[2], 0.94f);
        return Color.HSVToColor(Color.alpha(original), hsv);
    }

    private static ColorStateList textStates(int enabledColor) {
        return new ColorStateList(
                new int[][] {
                        new int[] {-android.R.attr.state_enabled},
                        new int[] {}
                },
                new int[] {DISABLED_TEXT, enabledColor});
    }

    private static ColorStateList preserveAlphaStates(
            ColorStateList original,
            int adaptedDefault) {
        int disabled = withAlpha(adaptedDefault, 0x61);
        return new ColorStateList(
                new int[][] {
                        new int[] {-android.R.attr.state_enabled},
                        new int[] {}
                },
                new int[] {disabled, adaptedDefault});
    }

    private static ColorStateList buttonTextStates(int enabledColor) {
        return new ColorStateList(
                new int[][] {
                        new int[] {-android.R.attr.state_enabled},
                        new int[] {}
                },
                new int[] {withAlpha(enabledColor, 0x61), enabledColor});
    }

    private static ColorStateList buttonBackgroundStates(int semantic, boolean primary) {
        int normalAlpha = primary ? 0x52 : 0x24;
        int pressedAlpha = primary ? 0x70 : 0x3D;
        int disabledAlpha = primary ? 0x20 : 0x12;
        return new ColorStateList(
                new int[][] {
                        new int[] {android.R.attr.state_enabled, android.R.attr.state_pressed},
                        new int[] {-android.R.attr.state_enabled},
                        new int[] {}
                },
                new int[] {
                        withAlpha(semantic, pressedAlpha),
                        withAlpha(semantic, disabledAlpha),
                        withAlpha(semantic, normalAlpha)
                });
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static String resourceEntryName(View view) {
        if (view == null || view.getId() == View.NO_ID) return "";
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable ignored) {
            return "";
        }
    }
}
