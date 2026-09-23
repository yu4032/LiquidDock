package com.hellovoid.liquiddock;

import android.content.res.ColorStateList;
import android.graphics.Color;
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

    // Lift only the vendor tint colors of buttons that already have a visible filled surface.
    // Transparent destructive buttons remain transparent; alpha and hue are preserved.
    private static final float BUTTON_RGB_SCALE = 1.08f;
    private static final int BUTTON_RGB_OFFSET = 12;
    private static final int MIN_VISIBLE_BUTTON_ALPHA = 24;

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
                ColorStateList originalTint = button.getBackgroundTintList();
                snapshot = new ButtonSurfaceSnapshot(originalTint, liftVisibleButtonTint(originalTint));
                buttonSurfaceSnapshots.put(button, snapshot);
            }

            if (snapshot.liftedBackgroundTint == null) {
                // No framework tint or an originally transparent surface: preserve MIUIX exactly.
                if (button.getBackgroundTintList() != snapshot.originalBackgroundTint) {
                    button.setBackgroundTintList(snapshot.originalBackgroundTint);
                }
                return;
            }

            // Some MIUIX button implementations re-apply their style during layout. Reassert only
            // the derived tint list; never replace the vendor Drawable or its state/shape logic.
            if (button.getBackgroundTintList() != snapshot.liftedBackgroundTint) {
                button.setBackgroundTintList(snapshot.liftedBackgroundTint);
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
        final ColorStateList originalBackgroundTint;
        final ColorStateList liftedBackgroundTint;

        ButtonSurfaceSnapshot(
                ColorStateList originalBackgroundTint,
                ColorStateList liftedBackgroundTint) {
            this.originalBackgroundTint = originalBackgroundTint;
            this.liftedBackgroundTint = liftedBackgroundTint;
        }
    }

    private static ColorStateList liftVisibleButtonTint(ColorStateList original) {
        if (original == null) return null;

        int[] pressedState = new int[] {
                android.R.attr.state_enabled, android.R.attr.state_pressed};
        int[] focusedState = new int[] {
                android.R.attr.state_enabled, android.R.attr.state_focused};
        int[] disabledState = new int[] {-android.R.attr.state_enabled};
        int[] defaultState = new int[] {};

        int defaultColor = original.getDefaultColor();
        int pressed = original.getColorForState(pressedState, defaultColor);
        int focused = original.getColorForState(focusedState, defaultColor);
        int disabled = original.getColorForState(disabledState, defaultColor);

        // Transparent destructive/neutral button surfaces are a semantic style, not a light-mode
        // artifact. Preserve them exactly instead of exposing the drawable's fallback blue fill.
        int maxAlpha = Math.max(
                Math.max(Color.alpha(defaultColor), Color.alpha(pressed)),
                Math.max(Color.alpha(focused), Color.alpha(disabled)));
        if (maxAlpha < MIN_VISIBLE_BUTTON_ALPHA) return null;

        return new ColorStateList(
                new int[][] {pressedState, focusedState, disabledState, defaultState},
                new int[] {
                        liftButtonColor(pressed),
                        liftButtonColor(focused),
                        liftButtonColor(disabled),
                        liftButtonColor(defaultColor)
                });
    }

    private static int liftButtonColor(int color) {
        int alpha = Color.alpha(color);
        if (alpha < MIN_VISIBLE_BUTTON_ALPHA) return color;
        int r = liftChannel(Color.red(color));
        int g = liftChannel(Color.green(color));
        int b = liftChannel(Color.blue(color));
        return Color.argb(alpha, r, g, b);
    }

    private static int liftChannel(int channel) {
        return Math.min(255, Math.round(channel * BUTTON_RGB_SCALE) + BUTTON_RGB_OFFSET);
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
