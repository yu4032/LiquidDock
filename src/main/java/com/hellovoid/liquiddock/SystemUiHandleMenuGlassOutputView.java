package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

/** Transparent Prismal output layer hosted behind the native Handle Menu controls. */
final class SystemUiHandleMenuGlassOutputView extends TextureView
        implements TextureView.SurfaceTextureListener {
    private final WeakReference<View> targetRef;
    private final SystemUiHandleMenuPrismalSession session;
    private final View.OnLayoutChangeListener targetLayoutListener;
    private Surface outputSurface;
    private boolean disposed;

    private SystemUiHandleMenuGlassOutputView(
            Context context,
            View target,
            SystemUiHandleMenuPrismalSession session) {
        super(context);
        targetRef = new WeakReference<>(target);
        this.session = session;
        setOpaque(false);
        setAlpha(0f);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSurfaceTextureListener(this);
        targetLayoutListener = (v, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            try {
                syncFromTarget();
            } catch (Throwable error) {
                safeLog("layout sync failed", error);
            }
        };
        target.addOnLayoutChangeListener(targetLayoutListener);
    }

    static SystemUiHandleMenuGlassOutputView attachInsideTarget(
            View target,
            SystemUiHandleMenuPrismalSession session) {
        if (!(target instanceof ViewGroup) || session == null) return null;
        ViewGroup group = (ViewGroup) target;
        SystemUiHandleMenuGlassOutputView output =
                new SystemUiHandleMenuGlassOutputView(target.getContext(), target, session);
        // Keep vendor measurement unchanged. Direct layout supplies the visual frame after the
        // native LinearLayout has finished measuring its real controls.
        group.addView(output, 0, new ViewGroup.LayoutParams(0, 0));
        output.syncFromTarget();
        return output;
    }

    boolean syncFromTarget() {
        View target = targetRef.get();
        if (disposed || target == null || getParent() != target) return false;
        int width = Math.max(1, target.getWidth());
        int height = Math.max(1, target.getHeight());
        session.refreshHostMapping();
        boolean changed = getLeft() != 0 || getTop() != 0
                || getRight() != width || getBottom() != height;
        if (changed) {
            layout(0, 0, width, height);
            session.resizeOutput(width, height);
        }
        if (getVisibility() != View.VISIBLE) {
            setVisibility(View.VISIBLE);
            changed = true;
        }
        return changed;
    }

    void setMaterialAlpha(float alpha) {
        if (disposed) return;
        float next = Math.max(0f, Math.min(1f, alpha));
        if (Math.abs(getAlpha() - next) > 0.001f) setAlpha(next);
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        View target = targetRef.get();
        if (target != null) {
            try { target.removeOnLayoutChangeListener(targetLayoutListener); }
            catch (Throwable ignored) {}
        }
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) {
            try { session.detachOutput(current); }
            catch (Throwable error) { safeLog("output detach failed", error); }
        }
        try {
            if (getParent() instanceof ViewGroup) {
                ((ViewGroup) getParent()).removeView(this);
            }
        } catch (Throwable error) {
            safeLog("output view removal failed", error);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        if (disposed || texture == null) return;
        Surface next = null;
        try {
            next = new Surface(texture);
            Surface old = outputSurface;
            outputSurface = next;
            if (old != null) session.detachOutput(old);
            session.attachOutput(next, Math.max(1, width), Math.max(1, height));
        } catch (Throwable error) {
            if (outputSurface == next) outputSurface = null;
            if (next != null) {
                try { next.release(); } catch (Throwable ignored) {}
            }
            safeLog("SurfaceTexture attach failed", error);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        if (disposed) return;
        try {
            session.resizeOutput(Math.max(1, width), Math.max(1, height));
        } catch (Throwable error) {
            safeLog("SurfaceTexture resize failed", error);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) {
            try { session.detachOutput(current); }
            catch (Throwable error) { safeLog("SurfaceTexture destroy detach failed", error); }
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {}

    private static void safeLog(String message, Throwable error) {
        try {
            Api101Bridge.log("[DC][SystemUiHandleMenuGlass] " + message, error);
        } catch (Throwable ignored) {
            // TextureView callbacks execute on SystemUI's UI thread and must never escape.
        }
    }
}
