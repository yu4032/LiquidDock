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
 * non-button text keeps chromatic semantic colors while neutral dark text is promoted for
 * contrast. Dialog buttons use the canonical MIUIX dark neutral/primary/danger tokens.</p>
 */
final class LauncherDialogDarkModeController {
    private static final int PRIMARY_TEXT = 0xFFFFFFFF;
    private static final int BODY_TEXT = 0xE6FFFFFF;
    private static final int COMMENT_TEXT = 0xB8FFFFFF;
    private static final int DISABLED_TEXT = 0x61FFFFFF;

    private static final int MIN_VISIBLE_ALPHA = 32;
    private static final int MAX_NEUTRAL_CHANNEL_SPREAD = 36;
    private static final int MAX_DARK_CHANNEL = 128;

    // Canonical MIUIX dark dialog tokens from HyperOS resources.
    private static final int DARK_NEUTRAL_BUTTON_BG = 0x24FFFFFF;
    private static final int DARK_NEUTRAL_BUTTON_BG_DISABLED = 0x14FFFFFF;
    private static final int DARK_NEUTRAL_BUTTON_TEXT = 0xCCFFFFFF;
    private static final int DARK_PRIMARY_BUTTON_BG = 0xFF4788FF;
    private static final int DARK_PRIMARY_BUTTON_BG_PRESSED = 0xFF3885F8;
    private static final int DARK_PRIMARY_BUTTON_BG_DISABLED = 0x4D4788FF;
    private static final int DARK_PRIMARY_BUTTON_TEXT = 0xE6FFFFFF;
    private static final int DARK_DANGER_BUTTON_TEXT = 0xFFFA4238;
    private static final int DARK_DANGER_BUTTON_TEXT_DISABLED = 0x4DFA4238;
    private static final int DARK_DISABLED_BUTTON_TEXT = 0x4DFFFFFF;

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
                    applyButtonAppearance(text);
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

        private void applyButtonAppearance(TextView button) {
            ButtonSurfaceSnapshot snapshot = buttonSurfaceSnapshots.get(button);
            if (snapshot == null) {
                TextSnapshot textSnapshot = textSnapshots.get(button);
                ColorStateList originalText = textSnapshot != null
                        ? textSnapshot.textColors : button.getTextColors();
                ButtonRole role = resolveButtonRole(
                        button,
                        originalText != null ? originalText.getDefaultColor() : button.getCurrentTextColor(),
                        button.getBackgroundTintList());
                snapshot = new ButtonSurfaceSnapshot(
                        button.getBackgroundTintList(),
                        role,
                        buttonBackgroundStates(role),
                        buttonTextStates(role));
                buttonSurfaceSnapshots.put(button, snapshot);
            }

            if (snapshot.darkBackgroundTint != null
                    && button.getBackgroundTintList() != snapshot.darkBackgroundTint) {
                // Preserve the vendor Drawable/shape/state machine; only substitute the MIUIX dark
                // token colors that the night theme would have supplied.
                button.setBackgroundTintList(snapshot.darkBackgroundTint);
            }
            if (snapshot.darkTextColors != null) {
                button.setTextColor(snapshot.darkTextColors);
            }
        }
    }

    private static final class TextSnapshot {
        final ColorStateList textColors;

        TextSnapshot(ColorStateList textColors) {
            this.textColors = textColors;
        }
    }

    private enum ButtonRole {
        NEUTRAL,
        PRIMARY,
        DANGER
    }

    private static final class ButtonSurfaceSnapshot {
        final ColorStateList originalBackgroundTint;
        final ButtonRole role;
        final ColorStateList darkBackgroundTint;
        final ColorStateList darkTextColors;

        ButtonSurfaceSnapshot(
                ColorStateList originalBackgroundTint,
                ButtonRole role,
                ColorStateList darkBackgroundTint,
                ColorStateList darkTextColors) {
            this.originalBackgroundTint = originalBackgroundTint;
            this.role = role;
            this.darkBackgroundTint = darkBackgroundTint;
            this.darkTextColors = darkTextColors;
        }
    }

    private static ButtonRole resolveButtonRole(
            TextView button,
            int originalTextColor,
            ColorStateList originalBackgroundTint) {
        if (isDangerRed(originalTextColor)) return ButtonRole.DANGER;

        int background = originalBackgroundTint != null
                ? originalBackgroundTint.getDefaultColor() : Color.TRANSPARENT;
        if (isPrimaryBlue(background)) return ButtonRole.PRIMARY;

        // MIUIX AlertDialog uses button1 as the positive/primary slot. Destructive positive
        // actions are already caught by the red-text test above, so they never become blue here.
        if ("button1".equals(resourceEntryName(button))) return ButtonRole.PRIMARY;
        return ButtonRole.NEUTRAL;
    }

    private static ColorStateList buttonBackgroundStates(ButtonRole role) {
        int[] pressed = new int[] {
                android.R.attr.state_enabled, android.R.attr.state_pressed};
        int[] disabled = new int[] {-android.R.attr.state_enabled};
        int[] normal = new int[] {};
        if (role == ButtonRole.PRIMARY) {
            return new ColorStateList(
                    new int[][] {pressed, disabled, normal},
                    new int[] {
                            DARK_PRIMARY_BUTTON_BG_PRESSED,
                            DARK_PRIMARY_BUTTON_BG_DISABLED,
                            DARK_PRIMARY_BUTTON_BG
                    });
        }
        // MIUIX danger buttons share the ordinary dialog button surface; only their text token is
        // danger-colored. This is why "卸载" remains red instead of becoming a blue primary CTA.
        return new ColorStateList(
                new int[][] {pressed, disabled, normal},
                new int[] {
                        DARK_NEUTRAL_BUTTON_BG,
                        DARK_NEUTRAL_BUTTON_BG_DISABLED,
                        DARK_NEUTRAL_BUTTON_BG
                });
    }

    private static ColorStateList buttonTextStates(ButtonRole role) {
        int[] disabled = new int[] {-android.R.attr.state_enabled};
        int[] normal = new int[] {};
        if (role == ButtonRole.DANGER) {
            return new ColorStateList(
                    new int[][] {disabled, normal},
                    new int[] {
                            DARK_DANGER_BUTTON_TEXT_DISABLED,
                            DARK_DANGER_BUTTON_TEXT
                    });
        }
        int normalColor = role == ButtonRole.PRIMARY
                ? DARK_PRIMARY_BUTTON_TEXT : DARK_NEUTRAL_BUTTON_TEXT;
        return new ColorStateList(
                new int[][] {disabled, normal},
                new int[] {DARK_DISABLED_BUTTON_TEXT, normalColor});
    }

    private static boolean isDangerRed(int color) {
        if (Color.alpha(color) < MIN_VISIBLE_ALPHA) return false;
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        return r >= 180 && r >= g + 48 && r >= b + 32;
    }

    private static boolean isPrimaryBlue(int color) {
        if (Color.alpha(color) < 96) return false;
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        return b >= 150 && b >= r + 48 && b >= g + 16;
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
