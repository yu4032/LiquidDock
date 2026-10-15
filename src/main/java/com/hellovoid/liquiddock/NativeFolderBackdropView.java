package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;

/**
 * Native compositor-owned folder backdrop, below the Prismal lens and OEM
 * FolderCling. Unlike the former full-screen GLES wallpaper quad, it never
 * replaces Workspace pixels with an opaque captured image.
 *
 * The compositor owns the background blur. The one-shot Prismal input is
 * retained solely for refraction within the actual glass silhouette.
 */
final class NativeFolderBackdropView extends View {
    private int radiusPx;
    private boolean claimed;

    NativeFolderBackdropView(Context context, int blurRadiusPx) {
        super(context);
        radiusPx = Math.max(0, Math.min(400, blurRadiusPx));
        setBackground(new ColorDrawable(Color.TRANSPARENT));
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setAlpha(0f);
    }

    void updateBlurRadius(int value) {
        int next = Math.max(0, Math.min(400, value));
        if (radiusPx == next) return;
        radiusPx = next;
        if (isAttachedToWindow()) applyNativeBlur();
    }

    void updateProgress(float amount) {
        float alpha = OpenFolderBackdropPolicy.backdropOpacity(amount);
        if (Float.compare(getAlpha(), alpha) != 0) setAlpha(alpha);
    }

    void dispose() {
        setAlpha(0f);
        if (getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) getParent()).removeView(this);
        } else if (claimed) {
            MiBlurBridge.clearPassWindowBlur(this);
            claimed = false;
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyNativeBlur();
    }

    private void applyNativeBlur() {
        if (claimed) MiBlurBridge.clearPassWindowBlur(this);
        claimed = radiusPx > 0 && MiBlurBridge.applyPassWindowBlur(this, radiusPx);
        MainHook.diag("[FolderTrace] native folder backdrop attached=" + isAttachedToWindow()
                + " requestedRadius=" + radiusPx + " applied=" + claimed
                + " window=" + getWindowToken());
    }

    @Override protected void onDetachedFromWindow() {
        if (claimed) MiBlurBridge.clearPassWindowBlur(this);
        claimed = false;
        super.onDetachedFromWindow();
    }
}
