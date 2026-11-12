package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

/** Transparent, non-interactive PassBlur authority.
 * Keep its panel BELOW the IME: FLAG_NOT_FOCUSABLE alone would otherwise
 * place this full-screen, FLAG_NOT_TOUCHABLE window above the keyboard,
 * causing Android 12+ cross-UID touch-occlusion rejection.
 */
final class ShortcutPopupSourceOverlay extends FrameLayout {
    interface Listener {
        void onAttached(ShortcutPopupSourceOverlay overlay);
        void onAttachFailed(Throwable error);
    }

    private final WindowManager windowManager;
    private boolean added;

    private ShortcutPopupSourceOverlay(Context context, WindowManager windowManager) {
        super(context);
        this.windowManager = windowManager;
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    static ShortcutPopupSourceOverlay attach(View launcherRoot, Listener listener) {
        return attach(launcherRoot, listener, "LiquidDockShortcutBackdropSource");
    }

    static ShortcutPopupSourceOverlay attach(View launcherRoot, Listener listener, String title) {
        return attachToBounds(launcherRoot, null, listener, title);
    }

    static ShortcutPopupSourceOverlay attachToLauncherBounds(
            View launcherRoot, Listener listener, String title) {
        if (launcherRoot == null) return null;
        int[] location = new int[2];
        launcherRoot.getLocationOnScreen(location);
        return attachToBounds(launcherRoot, new Rect(location[0], location[1],
                location[0] + launcherRoot.getWidth(), location[1] + launcherRoot.getHeight()),
                listener, title);
    }

    static ShortcutPopupSourceOverlay attachToBounds(
            View launcherRoot, Rect bounds, Listener listener, String title) {
        if (launcherRoot == null || launcherRoot.getWindowToken() == null) return null;
        Context context = launcherRoot.getContext();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return null;
        ShortcutPopupSourceOverlay overlay = new ShortcutPopupSourceOverlay(context, wm);
        overlay.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                if (listener != null) listener.onAttached(overlay);
            }

            @Override public void onViewDetachedFromWindow(View v) {}
        });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.token = launcherRoot.getWindowToken();
        lp.gravity = Gravity.TOP | Gravity.START;
        if (bounds != null) {
            // Match the output's logical pixels, including its screen origin.
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.width = bounds.width();
            lp.height = bounds.height();
            lp.x = bounds.left;
            lp.y = bounds.top;
            lp.setFitInsetsTypes(0);
        }
        lp.setTitle(title);
        try {
            wm.addView(overlay, lp);
            overlay.added = true;
            return overlay;
        } catch (Throwable error) {
            if (listener != null) listener.onAttachFailed(error);
            return null;
        }
    }

    void dispose() {
        if (!added) return;
        added = false;
        try { windowManager.removeViewImmediate(this); }
        catch (Throwable ignored) {}
    }
}
