package com.hellovoid.liquiddock;

/** Pure policy for changing only the night qualifier bits of Configuration.uiMode. */
public final class WidgetThemePolicy {
    static final int UI_MODE_NIGHT_MASK = 0x30;
    static final int UI_MODE_NIGHT_NO = 0x10;
    static final int UI_MODE_NIGHT_YES = 0x20;

    private WidgetThemePolicy() {}

    public static int applyToUiMode(int uiMode, String mode) {
        if ("light".equals(mode)) {
            return (uiMode & ~UI_MODE_NIGHT_MASK) | UI_MODE_NIGHT_NO;
        }
        return uiMode;
    }
}
