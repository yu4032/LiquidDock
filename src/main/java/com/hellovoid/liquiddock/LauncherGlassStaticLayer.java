package com.hellovoid.liquiddock;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** One transparent static Launcher glass output for an entire stable Launcher root. */
final class LauncherGlassStaticLayer extends TextureView implements TextureView.SurfaceTextureListener {
    private static final WeakHashMap<View, LauncherGlassStaticLayer> BY_ROOT = new WeakHashMap<>();

    private final WeakReference<View> rootRef;
    private final LauncherGlassSession session;
    private final Handler mainHandler;
    private Surface outputSurface;
    private boolean disposed;
    private ValueAnimator systemUiTimingAnimator;
    private final View.OnAttachStateChangeListener rootAttachListener;

    private LauncherGlassStaticLayer(Context context, View root, LauncherGlassSession session) {
        super(context);
        rootRef = new WeakReference<>(root);
        this.session = session;
        mainHandler = new Handler(context.getMainLooper());
        setOpaque(false);
        setVisibility(View.VISIBLE);
        setAlpha(0f);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSurfaceTextureListener(this);
        rootAttachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}

            @Override public void onViewDetachedFromWindow(View v) {
                mainHandler.post(() -> {
                    View stableRoot = rootRef.get();
                    if (stableRoot == v && !v.isAttachedToWindow()) {
                        forget(stableRoot, LauncherGlassStaticLayer.this);
                    }
                });
            }
        };
        root.addOnAttachStateChangeListener(rootAttachListener);
    }

    static synchronized LauncherGlassStaticLayer acquire(View root, LauncherGlassSession session) {
        if (root == null || session == null || !(root instanceof ViewGroup)
                || !root.isAttachedToWindow()) return null;
        LauncherGlassStaticLayer existing = BY_ROOT.get(root);
        if (existing != null && !existing.disposed && existing.getParent() == root) return existing;
        ViewGroup rootGroup = (ViewGroup) root;
        LauncherGlassStaticLayer layer = new LauncherGlassStaticLayer(root.getContext(), root, session);
        rootGroup.addView(layer, 0, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        BY_ROOT.put(root, layer);
        MainHook.log("[DC][LauncherGlass] shared static root layer attached root="
                + root.getClass().getSimpleName());
        return layer;
    }

    private static synchronized void forget(View root, LauncherGlassStaticLayer layer) {
        if (root != null && BY_ROOT.get(root) == layer) BY_ROOT.remove(root);
    }

    static void revealFromSystemUiTimingForAll(long sourceUptimeMs, long receiveUptimeMs) {
        ArrayList<LauncherGlassStaticLayer> snapshot;
        synchronized (LauncherGlassStaticLayer.class) {
            snapshot = new ArrayList<>(BY_ROOT.values());
        }
        for (LauncherGlassStaticLayer layer : snapshot) {
            if (layer != null) layer.revealFromSystemUiTiming(sourceUptimeMs, receiveUptimeMs);
        }
    }

    static void hideFromSystemUiTimingForAll() {
        ArrayList<LauncherGlassStaticLayer> snapshot;
        synchronized (LauncherGlassStaticLayer.class) {
            snapshot = new ArrayList<>(BY_ROOT.values());
        }
        for (LauncherGlassStaticLayer layer : snapshot) {
            if (layer != null) layer.hideFromSystemUiTiming();
        }
    }

    void setSceneVisible(boolean visible, boolean fadeReveal, boolean immediateHide) {
        if (disposed) return;
        cancelSystemUiTimingAnimator();
        animate().cancel();
        if (!visible && immediateHide) {
            setAlpha(0f);
            return;
        }
        if (visible && !fadeReveal) {
            setAlpha(1f);
            return;
        }
        LauncherGlassVisibilityTransition.Plan plan =
                LauncherGlassVisibilityTransition.plan(getAlpha(), visible);
        setAlpha(plan.startAlpha);
        if (plan.durationMs == 0L) {
            setAlpha(plan.targetAlpha);
            return;
        }
        animate().alpha(plan.targetAlpha)
                .setDuration(plan.durationMs)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void revealFromSystemUiTiming(long sourceUptimeMs, long receiveUptimeMs) {
        if (disposed) return;
        animate().cancel();
        cancelSystemUiTimingAnimator();
        LauncherGlassVisibilityTransition.Plan plan =
                LauncherGlassVisibilityTransition.plan(getAlpha(), true);
        setAlpha(plan.startAlpha);
        if (plan.durationMs == 0L) {
            setAlpha(1f);
            return;
        }
        long elapsedMs = SystemUiHomeTransitionTimingPolicy.elapsedMs(
                sourceUptimeMs, receiveUptimeMs, plan.durationMs);
        if (elapsedMs >= plan.durationMs) {
            setAlpha(plan.targetAlpha);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(plan.startAlpha, plan.targetAlpha);
        systemUiTimingAnimator = animator;
        animator.setDuration(plan.durationMs);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            if (!disposed && systemUiTimingAnimator == valueAnimator) {
                Object value = valueAnimator.getAnimatedValue();
                if (value instanceof Float) setAlpha((Float) value);
            }
        });
        animator.start();
        if (elapsedMs > 0L) animator.setCurrentPlayTime(elapsedMs);
    }

    private void hideFromSystemUiTiming() {
        if (disposed) return;
        animate().cancel();
        cancelSystemUiTimingAnimator();
        setAlpha(0f);
    }

    private void cancelSystemUiTimingAnimator() {
        ValueAnimator current = systemUiTimingAnimator;
        systemUiTimingAnimator = null;
        if (current != null) current.cancel();
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        cancelSystemUiTimingAnimator();
        View root = rootRef.get();
        if (root != null) {
            root.removeOnAttachStateChangeListener(rootAttachListener);
            forget(root, this);
        }
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        if (disposed || texture == null) return;
        Surface next = new Surface(texture);
        Surface old = outputSurface;
        outputSurface = next;
        if (old != null) session.detachStaticOutput(old);
        session.attachStaticOutput(next, Math.max(1, width), Math.max(1, height));
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        if (!disposed) session.resizeStaticOutput(Math.max(1, width), Math.max(1, height));
    }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachStaticOutput(current);
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
}
