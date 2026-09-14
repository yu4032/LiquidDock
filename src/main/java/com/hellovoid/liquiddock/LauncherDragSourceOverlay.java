package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Transparent application-panel root above Launcher used as the live drag-glass authority.
 *
 * <p>The whole window is excluded from its own PassBlur source. Glass output and the visual
 * mirror of the dragged object therefore live above Launcher while the producer continuously
 * samples the real workspace underneath.</p>
 */
final class LauncherDragSourceOverlay extends FrameLayout {
    interface Listener {
        void onAttached(LauncherDragSourceOverlay overlay);
        void onAttachFailed(Throwable error);
    }

    private final WindowManager windowManager;
    private final FrameLayout glassHost;
    private final FrameLayout mirrorHost;
    private boolean added;

    private LauncherDragSourceOverlay(Context context, WindowManager windowManager) {
        super(context);
        this.windowManager = windowManager;
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        glassHost = createHost(context);
        mirrorHost = createHost(context);
        addView(glassHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(mirrorHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static FrameLayout createHost(Context context) {
        FrameLayout host = new FrameLayout(context);
        host.setBackgroundColor(Color.TRANSPARENT);
        host.setClickable(false);
        host.setFocusable(false);
        host.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return host;
    }

    FrameLayout glassHost() {
        return glassHost;
    }

    FrameLayout mirrorHost() {
        return mirrorHost;
    }

    static LauncherDragSourceOverlay attach(View launcherRoot, Listener listener) {
        if (launcherRoot == null || launcherRoot.getWindowToken() == null) return null;
        Context context = launcherRoot.getContext();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return null;

        LauncherDragSourceOverlay overlay = new LauncherDragSourceOverlay(context, wm);
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
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.token = launcherRoot.getWindowToken();
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setTitle("LiquidDockDragBackdropSource");
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
