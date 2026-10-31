package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.view.View;

import com.hellovoid.prismal.PrismalEdgeDecoration;
import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;

/** Frozen Gboard floating glass drawn in the keyboard's ViewRoot frame. */
final class GboardFloatingGlassHwuiView extends View {
    interface Listener {
        void onPresented();
        void onFailure(Throwable error);
    }

    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final float[] invariants = new float[10];
    private Bitmap rawBackdrop;
    private Bitmap blurredBackdrop;
    private RuntimeShader shader;
    private GboardFloatingGlassGeometry geometry;
    private PrismalParams params;
    private PrismalHighlightProfile highlights;
    private PrismalEdgeDecoration decoration;
    private float density = 1f;
    private int logicalWidth;
    private int logicalHeight;
    private boolean failureReported;

    GboardFloatingGlassHwuiView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setWillNotDraw(false);
        setBackgroundColor(0x00000000);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Bind a new immutable prepared backdrop pair. These bitmaps are never recycled here. */
    boolean setBackdrops(
            Bitmap raw,
            Bitmap blurred,
            int nextLogicalWidth,
            int nextLogicalHeight) {
        if (raw == null || blurred == null || raw.isRecycled() || blurred.isRecycled()
                || nextLogicalWidth <= 0 || nextLogicalHeight <= 0) return false;
        try {
            RuntimeShader nextShader = new RuntimeShader(
                    LauncherGlassHwuiProbeView.agslSource(true));
            BitmapShader rawInput = new BitmapShader(
                    raw, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            BitmapShader blurInput = new BitmapShader(
                    blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            rawInput.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
            blurInput.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
            nextShader.setInputShader("u_backgroundTexture", rawInput);
            nextShader.setInputShader("u_blurredTexture", blurInput);
            nextShader.setFloatUniform("uProbeRawScale",
                    (float) raw.getWidth() / nextLogicalWidth,
                    (float) raw.getHeight() / nextLogicalHeight);
            nextShader.setFloatUniform("uProbeBlurScale",
                    (float) blurred.getWidth() / nextLogicalWidth,
                    (float) blurred.getHeight() / nextLogicalHeight);
            nextShader.setFloatUniform("uProbeWallpaperScale", 1f);
            rawBackdrop = raw;
            blurredBackdrop = blurred;
            logicalWidth = nextLogicalWidth;
            logicalHeight = nextLogicalHeight;
            shader = nextShader;
            failureReported = false;
            invalidate();
            return true;
        } catch (Throwable error) {
            reportFailure(error);
            return false;
        }
    }

    /** Publish root-local lens geometry and material for the next native keyboard draw. */
    void setScene(
            GboardFloatingGlassGeometry nextGeometry,
            PrismalParams nextParams,
            PrismalHighlightProfile nextHighlights,
            PrismalEdgeDecoration nextDecoration,
            float nextDensity) {
        geometry = nextGeometry;
        params = nextParams;
        highlights = nextHighlights;
        decoration = nextDecoration;
        density = Float.isFinite(nextDensity) && nextDensity > 0f ? nextDensity : 1f;
        invalidate();
    }

    /** Drop Java/Skia references; HWUI owns any in-flight draw references. */
    void clearBackdrops() {
        shader = null;
        rawBackdrop = null;
        blurredBackdrop = null;
        logicalWidth = 0;
        logicalHeight = 0;
        geometry = null;
        params = null;
        highlights = null;
        decoration = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        RuntimeShader activeShader = shader;
        GboardFloatingGlassGeometry activeGeometry = geometry;
        PrismalParams activeParams = params;
        PrismalHighlightProfile activeHighlights = highlights;
        PrismalEdgeDecoration activeDecoration = decoration;
        if (activeShader == null || rawBackdrop == null || blurredBackdrop == null
                || activeGeometry == null || activeParams == null
                || activeHighlights == null || activeDecoration == null) return;
        if (getWidth() != logicalWidth || getHeight() != logicalHeight
                || activeGeometry.rootWidth != logicalWidth
                || activeGeometry.rootHeight != logicalHeight) return;
        if (!canvas.isHardwareAccelerated()) {
            reportFailure(new IllegalStateException("Gboard glass ViewRoot is not HWUI accelerated"));
            return;
        }

        try {
            float minDimension = Math.min(activeGeometry.width, activeGeometry.height);
            activeShader.setFloatUniform("u_resolution", (float) logicalWidth, (float) logicalHeight);
            activeShader.setFloatUniform("u_glassSize",
                    activeGeometry.width, activeGeometry.height);
            activeShader.setFloatUniform("u_cornerRadii",
                    activeGeometry.cornerRadius, activeGeometry.cornerRadius,
                    activeGeometry.cornerRadius, activeGeometry.cornerRadius);
            activeShader.setFloatUniform("uProbeBounds",
                    activeGeometry.left, activeGeometry.top,
                    activeGeometry.left + activeGeometry.width,
                    activeGeometry.top + activeGeometry.height);
            LauncherGlassSession.setHwuiProbeMaterialUniforms(
                    activeShader, activeParams, activeHighlights, minDimension, invariants);
            LauncherGlassSession.setHwuiProbeDecorationUniforms(
                    activeShader, activeDecoration, density, minDimension);

            float guard = 3f + activeDecoration.extraGuardPx(density, minDimension);
            paint.setShader(activeShader);
            paint.setAlpha(255);
            canvas.drawRect(
                    activeGeometry.left - guard,
                    activeGeometry.top - guard,
                    activeGeometry.left + activeGeometry.width + guard,
                    activeGeometry.top + activeGeometry.height + guard,
                    paint);
            paint.setShader(null);
            if (listener != null) listener.onPresented();
        } catch (Throwable error) {
            paint.setShader(null);
            reportFailure(error);
        }
    }

    private void reportFailure(Throwable error) {
        if (failureReported) return;
        failureReported = true;
        if (listener != null) listener.onFailure(error);
    }
}
