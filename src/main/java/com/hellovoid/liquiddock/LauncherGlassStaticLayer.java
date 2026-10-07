package com.hellovoid.liquiddock;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** One transparent static Launcher glass output for an entire stable Launcher root. */
final class LauncherGlassStaticLayer extends TextureView implements TextureView.SurfaceTextureListener {
    private static final WeakHashMap<View, LauncherGlassStaticLayer> BY_ROOT = new WeakHashMap<>();

    private final WeakReference<View> rootRef;
    private WeakReference<View> workspaceRef = new WeakReference<>(null);
    private final LauncherGlassSession session;
    private final Handler mainHandler;
    private Surface outputSurface;
    private boolean disposed;
    private final LauncherGlassLayerAlpha layerAlpha = new LauncherGlassLayerAlpha();
    private ValueAnimator sceneAnimator;
    private ValueAnimator motionAnimator;
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
                        cancelSceneAnimation();
                        cancelMotionAnimation();
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
        if (session.isWorkspaceUnlockMotionActive()) layer.startMotionReveal();
        MainHook.log("[DC][LauncherGlass] shared static root layer attached root="
                + root.getClass().getSimpleName());
        return layer;
    }

    private static synchronized LauncherGlassStaticLayer find(View root) {
        LauncherGlassStaticLayer layer = root != null ? BY_ROOT.get(root) : null;
        return layer != null && !layer.disposed ? layer : null;
    }

    private static synchronized void forget(View root, LauncherGlassStaticLayer layer) {
        if (root != null && BY_ROOT.get(root) == layer) BY_ROOT.remove(root);
    }

    static void onWorkspaceScrollMutation(View workspace, int beforeScrollX, int afterScrollX) {
        if (workspace == null || beforeScrollX == afterScrollX) return;
        View root = workspace.getRootView();
        LauncherGlassStaticLayer layer = find(root);
        if (layer == null) return;
        layer.workspaceRef = new WeakReference<>(workspace);
        // Do not translate the root-wide TextureView. Its pixels include the already sampled
        // backdrop, which belongs to root coordinates. Project only glass geometry in the session.
        layer.session.onWorkspaceScrollMutation(beforeScrollX, afterScrollX);
    }

    static Integer captureWorkspaceScrollAnchor(View root) {
        LauncherGlassStaticLayer layer = find(root);
        if (layer == null) return null;
        View workspace = layer.workspaceRef.get();
        if (workspace == null || !workspace.isAttachedToWindow()
                || workspace.getRootView() != root) return null;
        return workspace.getScrollX();
    }

    static void onWorkspaceUnlockMotionStarted(View root) {
        LauncherGlassStaticLayer layer = find(root);
        if (layer == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) layer.startMotionReveal();
        else layer.mainHandler.post(layer::startMotionReveal);
    }

    private void startMotionReveal() {
        if (disposed) return;
        cancelMotionAnimation();
        long duration = AnimationRuntimeState.workspaceVisibilityDurationMs();
        boolean animate = layerAlpha.startMotionReveal(duration);
        applyLayerAlpha();
        if (!animate) return;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        motionAnimator = animator;
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            if (disposed || motionAnimator != value) return;
            layerAlpha.setMotionAlpha((Float) value.getAnimatedValue());
            applyLayerAlpha();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (motionAnimator == animation) motionAnimator = null;
            }
        });
        animator.start();
        MainHook.log("[DC][WorkspaceFade] unlock reveal durationMs=" + duration);
    }

    void setSceneVisible(boolean visible, boolean fadeReveal, boolean immediateHide) {
        if (disposed) return;
        cancelSceneAnimation();
        if (!visible && immediateHide) {
            layerAlpha.setSceneAlpha(0f);
            applyLayerAlpha();
            return;
        }
        if (visible && !fadeReveal) {
            layerAlpha.setSceneAlpha(1f);
            applyLayerAlpha();
            return;
        }
        LauncherGlassVisibilityTransition.Plan plan =
                LauncherGlassVisibilityTransition.plan(layerAlpha.sceneAlpha(), visible);
        layerAlpha.setSceneAlpha(plan.startAlpha);
        applyLayerAlpha();
        if (plan.durationMs == 0L) {
            layerAlpha.setSceneAlpha(plan.targetAlpha);
            applyLayerAlpha();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(plan.startAlpha, plan.targetAlpha);
        sceneAnimator = animator;
        animator.setDuration(plan.durationMs);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            if (disposed || sceneAnimator != value) return;
            layerAlpha.setSceneAlpha((Float) value.getAnimatedValue());
            applyLayerAlpha();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (sceneAnimator == animation) sceneAnimator = null;
            }
        });
        animator.start();
    }

    private void applyLayerAlpha() { setAlpha(layerAlpha.alpha()); }

    private void cancelSceneAnimation() {
        ValueAnimator previous = sceneAnimator;
        sceneAnimator = null;
        if (previous != null) previous.cancel();
    }

    private void cancelMotionAnimation() {
        ValueAnimator previous = motionAnimator;
        motionAnimator = null;
        if (previous != null) previous.cancel();
    }

    void dispose() {
        if (disposed) return;
        session.resetWorkspaceScrollProjection();
        disposed = true;
        cancelSceneAnimation();
        cancelMotionAnimation();
        View root = rootRef.get();
        if (root != null) {
            root.removeOnAttachStateChangeListener(rootAttachListener);
            forget(root, this);
        }
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        if (disposed || texture == null) return;
        session.resetWorkspaceScrollProjection();
        Surface next = new Surface(texture);
        Surface old = outputSurface;
        outputSurface = next;
        if (old != null) session.detachStaticOutput(old);
        session.attachStaticOutput(next, Math.max(1, width), Math.max(1, height));
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        if (disposed) return;
        session.resetWorkspaceScrollProjection();
        session.resizeStaticOutput(Math.max(1, width), Math.max(1, height));
    }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        session.resetWorkspaceScrollProjection();
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachStaticOutput(current);
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        // The presented root-wide surface is intentionally never transformed after swap. The
        // backdrop stays root-anchored while the renderer late-projects glass geometry itself.
    }
}
