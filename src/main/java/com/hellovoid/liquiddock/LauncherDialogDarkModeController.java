package com.hellovoid.liquiddock;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Color-aware dark content treatment for the exact Launcher dialog panel.
 *
 * <p>Dark mode only adapts text and the real MIUIX button surfaces. It never tints ImageViews or
 * TextView compound drawables, so app icons and semantic artwork remain vendor-owned. Chromatic
 * text (for example destructive red) is preserved exactly; only neutral dark text is promoted to
 * a light color for contrast.</p>
 */
final class LauncherDialogDarkModeController {
    private static final int PRIMARY_TEXT = 0xFFFFFFFF;
    private static final int BODY_TEXT = 0xE6FFFFFF;
    private static final int COMMENT_TEXT = 0xB8FFFFFF;
    private static final int DISABLED_TEXT = 0x61FFFFFF;

    private static final int MIN_VISIBLE_ALPHA = 32;
    private static final int MAX_NEUTRAL_CHANNEL_SPREAD = 36;
    private static final int MAX_DARK_CHANNEL = 128;

    // A small additive/multiplicative lift keeps the vendor button recognizable while making its
    // surface separate from a dark dialog. The original drawable/state machine is cloned intact.
    private static final float BUTTON_RGB_SCALE = 1.08f;
    private static final float BUTTON_RGB_OFFSET = 12f;

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
        private final Map<View, ButtonSurfaceSnapshot> buttonSurfaceSnapshots =
                new IdentityHashMap<>();
        private boolean restored;

        Session(View root) {
            this.root = root;
        }

        void reapply() {
            if (restored || root == null) return;
            applyRecursive(root, false);
        }

        void restore() {
            if (restored) return;
            restored = true;

            for (Map.Entry<View, ButtonSurfaceSnapshot> entry
                    : new ArrayList<>(buttonSurfaceSnapshots.entrySet())) {
                View view = entry.getKey();
                ButtonSurfaceSnapshot snapshot = entry.getValue();
                if (view == null || snapshot == null) continue;
                try {
                    view.setBackground(snapshot.originalBackground);
                    view.setBackgroundTintList(snapshot.originalBackgroundTint);
                } catch (Throwable ignored) {}
            }

            for (Map.Entry<TextView, TextSnapshot> entry
                    : new ArrayList<>(textSnapshots.entrySet())) {
                TextView text = entry.getKey();
                TextSnapshot snapshot = entry.getValue();
                if (text == null || snapshot == null) continue;
                try {
                    text.setTextColor(snapshot.textColors);
                } catch (Throwable ignored) {}
            }

            buttonSurfaceSnapshots.clear();
            textSnapshots.clear();
        }

        private void applyRecursive(View view, boolean inButtonPanel) {
            boolean buttonPanel = inButtonPanel || "buttonPanel".equals(resourceEntryName(view));
            if (view instanceof TextView) {
                TextView text = (TextView) view;
                applyText(text);
                if (isDialogButton(text, buttonPanel)) {
                    applyButtonSurface(text);
                }
            }

            if (!(view instanceof ViewGroup)) return;
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyRecursive(group.getChildAt(i), buttonPanel);
            }
        }

        private void applyText(TextView text) {
            TextSnapshot snapshot = textSnapshots.get(text);
            if (snapshot == null) {
                snapshot = new TextSnapshot(text.getTextColors());
                textSnapshots.put(text, snapshot);
            }

            int original = snapshot.textColors.getDefaultColor();
            if (!isNearBlackNeutral(original)) {
                // Preserve destructive red, accent blue, and already-light vendor colors exactly.
                text.setTextColor(snapshot.textColors);
                return;
            }

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
        }

        private void applyButtonSurface(View button) {
            ButtonSurfaceSnapshot snapshot = buttonSurfaceSnapshots.get(button);
            if (snapshot == null) {
                snapshot = new ButtonSurfaceSnapshot(
                        button.getBackground(),
                        button.getBackgroundTintList());
                buttonSurfaceSnapshots.put(button, snapshot);
            }

            if (snapshot.liftedBackground == null) {
                snapshot.liftedBackground = makeLiftedBackground(button, snapshot.originalBackground);
            }
            if (snapshot.liftedBackground == null) return;

            // Some MIUIX button implementations re-apply their style during layout. Reassert the
            // cloned vendor drawable during pre-draw without replacing listeners or geometry.
            button.setBackgroundTintList(null);
            if (button.getBackground() != snapshot.liftedBackground) {
                button.setBackground(snapshot.liftedBackground);
            }
        }
    }

    private static final class TextSnapshot {
        final ColorStateList textColors;

        TextSnapshot(ColorStateList textColors) {
            this.textColors = textColors;
        }
    }

    private static final class ButtonSurfaceSnapshot {
        final Drawable originalBackground;
        final ColorStateList originalBackgroundTint;
        Drawable liftedBackground;

        ButtonSurfaceSnapshot(
                Drawable originalBackground,
                ColorStateList originalBackgroundTint) {
            this.originalBackground = originalBackground;
            this.originalBackgroundTint = originalBackgroundTint;
        }
    }

    private static Drawable makeLiftedBackground(View owner, Drawable original) {
        if (owner == null || original == null) return null;
        Drawable.ConstantState state = original.getConstantState();
        if (state == null) return null;
        try {
            Drawable clone = state.newDrawable(
                    owner.getResources(), owner.getContext().getTheme()).mutate();
            ColorMatrix lift = new ColorMatrix(new float[] {
                    BUTTON_RGB_SCALE, 0f, 0f, 0f, BUTTON_RGB_OFFSET,
                    0f, BUTTON_RGB_SCALE, 0f, 0f, BUTTON_RGB_OFFSET,
                    0f, 0f, BUTTON_RGB_SCALE, 0f, BUTTON_RGB_OFFSET,
                    0f, 0f, 0f, 1f, 0f
            });
            clone.setColorFilter(new ColorMatrixColorFilter(lift));
            return clone;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isDialogButton(TextView text, boolean inButtonPanel) {
        if (text == null) return false;
        String id = resourceEntryName(text);
        if ("button1".equals(id) || "button2".equals(id) || "button3".equals(id)) return true;
        if (!inButtonPanel) return false;
        return text.isClickable() || text.isFocusable();
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

    private static ColorStateList textStates(int enabledColor) {
        return new ColorStateList(
                new int[][] {
                        new int[] {-android.R.attr.state_enabled},
                        new int[] {}
                },
                new int[] {DISABLED_TEXT, enabledColor});
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
