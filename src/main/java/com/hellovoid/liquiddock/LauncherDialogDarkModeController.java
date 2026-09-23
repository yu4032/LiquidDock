package com.hellovoid.liquiddock;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dark content treatment for the exact Launcher dialog panel.
 *
 * <p>MIUIX explicitly disables Android force-dark on its dialog buttons, so relying on force-dark
 * does not style the controls consistently. This controller keeps vendor hierarchy/listeners and
 * drawable shapes intact, changing only text/compound-drawable colors and button background tint.
 * Original state is captured by identity and restored on fail-closed release.</p>
 */
final class LauncherDialogDarkModeController {
    private static final int PRIMARY_TEXT = 0xFFFFFFFF;
    private static final int BODY_TEXT = 0xE6FFFFFF;
    private static final int COMMENT_TEXT = 0xB8FFFFFF;
    private static final int DISABLED_TEXT = 0x61FFFFFF;
    private static final ColorStateList WHITE_COMPOUND_TINT =
            ColorStateList.valueOf(Color.WHITE);

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
                    button.setBackgroundTintList(snapshot.backgroundTint);
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
            textSnapshots.clear();
        }

        private void applyRecursive(View view) {
            if (view instanceof TextView) {
                applyText((TextView) view);
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    applyRecursive(group.getChildAt(i));
                }
            }
        }

        private void applyText(TextView text) {
            TextSnapshot textSnapshot = textSnapshots.get(text);
            if (textSnapshot == null) {
                textSnapshot = new TextSnapshot(
                        text.getTextColors(), text.getCompoundDrawableTintList());
                textSnapshots.put(text, textSnapshot);
            }

            if (text instanceof Button) {
                Button button = (Button) text;
                ButtonSnapshot buttonSnapshot = buttonSnapshots.get(button);
                if (buttonSnapshot == null) {
                    buttonSnapshot = new ButtonSnapshot(button.getBackgroundTintList());
                    buttonSnapshots.put(button, buttonSnapshot);
                }
                int semantic = semanticButtonText(textSnapshot.textColors.getDefaultColor());
                boolean primary = "button1".equals(resourceEntryName(button));
                button.setTextColor(buttonTextStates(semantic));
                button.setCompoundDrawableTintList(ColorStateList.valueOf(semantic));
                button.setBackgroundTintList(buttonBackgroundStates(semantic, primary));
                return;
            }

            String id = resourceEntryName(text);
            int color;
            if (id.toLowerCase().contains("title")) {
                color = PRIMARY_TEXT;
            } else if (id.toLowerCase().contains("comment")
                    || id.toLowerCase().contains("summary")) {
                color = COMMENT_TEXT;
            } else {
                color = BODY_TEXT;
            }
            text.setTextColor(textStates(color));
            text.setCompoundDrawableTintList(WHITE_COMPOUND_TINT);
        }
    }

    private static final class TextSnapshot {
        final ColorStateList textColors;
        final ColorStateList compoundTint;

        TextSnapshot(ColorStateList textColors, ColorStateList compoundTint) {
            this.textColors = textColors;
            this.compoundTint = compoundTint;
        }
    }

    private static final class ButtonSnapshot {
        final ColorStateList backgroundTint;

        ButtonSnapshot(ColorStateList backgroundTint) {
            this.backgroundTint = backgroundTint;
        }
    }

    private static ColorStateList textStates(int enabledColor) {
        return new ColorStateList(
                new int[][] {
                        new int[] {-android.R.attr.state_enabled},
                        new int[] {}
                },
                new int[] {DISABLED_TEXT, enabledColor});
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
        int normalAlpha = primary ? 0x66 : 0x28;
        int pressedAlpha = primary ? 0x82 : 0x42;
        int disabledAlpha = primary ? 0x24 : 0x14;
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

    /**
     * Preserve MIUIX semantic button hue (accent/destructive) when it is chromatic. Neutral dark
     * text becomes white. Saturated colors are lifted for contrast on the dark glass.
     */
    private static int semanticButtonText(int original) {
        float[] hsv = new float[3];
        Color.colorToHSV(original, hsv);
        if (hsv[1] < 0.18f) return Color.WHITE;
        hsv[1] = Math.min(hsv[1], 0.78f);
        hsv[2] = Math.max(hsv[2], 0.92f);
        return Color.HSVToColor(255, hsv);
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
