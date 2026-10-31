package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.view.View;

/** Draws cached popup/folder glass in the owning ViewRoot's HWUI frame. */
final class ShortcutPopupGlassHwuiView extends View {
    private final ShortcutPopupGlassSession session;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final float[] invariants = new float[10];
    private Bitmap backdrop;
    private Bitmap blurredBackdrop;
    private RuntimeShader baselineShader;
    private RuntimeShader decoratedShader;
    private RuntimeShader motionDecoratedShader;
    private LauncherGlassGeometry.Snapshot geometry;
    private float openProgress;
    private int logicalWidth;
    private int logicalHeight;
    private Runnable failureListener;
    private boolean drawFailureReported;

    ShortcutPopupGlassHwuiView(Context context, ShortcutPopupGlassSession session) {
        super(context);
        this.session = session;
        setWillNotDraw(false);
        setBackgroundColor(0x00000000);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setFailureListener(Runnable listener) {
        failureListener = listener;
    }

    boolean setBackdrops(Bitmap rawBitmap, Bitmap blurBitmap, int width, int height) {
        if (rawBitmap == null || blurBitmap == null || width <= 0 || height <= 0) return false;
        try {
            if (baselineShader == null || decoratedShader == null) {
                RuntimeShader nextBaseline = new RuntimeShader(
                        LauncherGlassHwuiProbeView.agslSource(false));
                RuntimeShader nextDecorated = new RuntimeShader(
                        LauncherGlassHwuiProbeView.agslSource(true));
                RuntimeShader nextMotionDecorated = null;
                try {
                    nextMotionDecorated = new RuntimeShader(
                            LauncherGlassHwuiProbeView.agslSource(true, true));
                } catch (RuntimeException ignored) {
                    // Use the full material if this device rejects the optional motion variant.
                }
                baselineShader = nextBaseline;
                decoratedShader = nextDecorated;
                motionDecoratedShader = nextMotionDecorated;
            }

            BitmapShader rawInput = new BitmapShader(
                    rawBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            BitmapShader blurInput = new BitmapShader(
                    blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            // BitmapShader defaults to NEAREST as a RuntimeShader child, regardless of
            // Paint.FILTER_BITMAP_FLAG. Explicit linear filtering is essential for the
            // half-resolution prepared blur and subpixel refraction offsets.
            rawInput.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
            blurInput.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
            bindBackdrop(baselineShader, rawInput, blurInput, rawBitmap, blurBitmap, width, height);
            bindBackdrop(decoratedShader, rawInput, blurInput, rawBitmap, blurBitmap, width, height);
            if (motionDecoratedShader != null) {
                bindBackdrop(motionDecoratedShader, rawInput, blurInput,
                        rawBitmap, blurBitmap, width, height);
            }

            backdrop = rawBitmap;
            blurredBackdrop = blurBitmap;
            logicalWidth = width;
            logicalHeight = height;
            drawFailureReported = false;
            invalidate();
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static void bindBackdrop(
            RuntimeShader shader, BitmapShader rawInput, BitmapShader blurInput,
            Bitmap rawBitmap, Bitmap blurBitmap, int width, int height) {
        shader.setInputShader("u_backgroundTexture", rawInput);
        shader.setInputShader("u_blurredTexture", blurInput);
        shader.setFloatUniform("uProbeRawScale",
                (float) rawBitmap.getWidth() / width,
                (float) rawBitmap.getHeight() / height);
        shader.setFloatUniform("uProbeBlurScale",
                (float) blurBitmap.getWidth() / width,
                (float) blurBitmap.getHeight() / height);
        // This shared AGSL shader uses the uniform to zoom sample coordinates. Popup
        // backdrops are already captured in their display scale, so keep their UV mapping
        // neutral; leaving the uniform at its default zero clamps samples to texture corners.
        shader.setFloatUniform("uProbeWallpaperScale", 1f);
    }

    void updateFrame(LauncherGlassGeometry.Snapshot next, float progress) {
        float clamped = OpenFolderBackdropPolicy.backdropOpacity(progress);
        if (geometry != null && geometry.sameAs(next)
                && Math.abs(openProgress - clamped) < 0.001f) return;
        geometry = next;
        openProgress = clamped;
        invalidate();
    }

    void clearBackdrops() {
        backdrop = null;
        blurredBackdrop = null;
        baselineShader = null;
        decoratedShader = null;
        motionDecoratedShader = null;
        logicalWidth = 0;
        logicalHeight = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (backdrop == null || blurredBackdrop == null) return;
        if ((getWidth() != logicalWidth || getHeight() != logicalHeight) && geometry != null) {
            Runnable listener = failureListener;
            if (listener != null) listener.run();
            return;
        }
        try {
            boolean drew = session.drawHwui(canvas, paint, geometry, openProgress,
                    baselineShader, decoratedShader, motionDecoratedShader, invariants);
            if (drew) {
                session.onHwuiPresented();
            } else if (geometry != null) {
                Runnable listener = failureListener;
                if (listener != null) listener.run();
            }
        } catch (Throwable error) {
            if (drawFailureReported) return;
            drawFailureReported = true;
            MainHook.log("[DC][OpenFolderGlass] HWUI lens draw failed: " + error);
            Runnable listener = failureListener;
            if (listener != null) listener.run();
        }
    }
}
