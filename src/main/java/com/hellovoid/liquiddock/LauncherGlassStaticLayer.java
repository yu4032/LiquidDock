package com.hellovoid.liquiddock;

import android.graphics.Bitmap;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** One transparent static Launcher glass output for an entire stable Launcher root. */
final class LauncherGlassStaticLayer extends TextureView implements TextureView.SurfaceTextureListener {
    private static final WeakHashMap<View, LauncherGlassStaticLayer> BY_ROOT = new WeakHashMap<>();

    private static final WeakHashMap<View, Long> UNLOCK_END_BY_ROOT = new WeakHashMap<>();
    private static final AtomicInteger HWUI_PROBE_SCROLL_MUTATIONS = new AtomicInteger();
    private static final DecelerateInterpolator APP_RETURN_INTERPOLATOR =
            new DecelerateInterpolator();
    private final WeakReference<View> rootRef;
    private WeakReference<View> workspaceRef = new WeakReference<>(null);
    private final LauncherGlassSession session;
    private final Handler mainHandler;
    private Surface outputSurface;
    private boolean disposed;
    private volatile boolean hwuiProbeActive;
    private volatile boolean hwuiProbeWallpaperScaleDirty;
    private boolean launchProxyActive;
    private boolean launchProxyReturning;
    private boolean launchAwaySuppressionLatched;
    private float launchReturnAlpha = 1f;
    private boolean popupCaptureSuppressed;
    private final LauncherGlassHwuiProbeView hwuiProbe;
    private final LauncherGlassLayerAlpha layerAlpha = new LauncherGlassLayerAlpha();
    private ValueAnimator sceneAnimator;
    private ValueAnimator motionAnimator;
    private ValueAnimator launchReturnAnimator;
    private long launchReturnStartedAtMs;
    private float launchReturnStartProgress;
    private float launchReturnLastProgress;
    private boolean launchReturnFadeStarted;
    private final View.OnAttachStateChangeListener rootAttachListener;
    private final Runnable clearLaunchAwaySuppression;

    private LauncherGlassStaticLayer(Context context, View root, LauncherGlassSession session) {
        super(context);
        rootRef = new WeakReference<>(root);
        this.session = session;
        mainHandler = new Handler(context.getMainLooper());
        clearLaunchAwaySuppression = () -> {
            if (disposed || launchProxyReturning || !launchAwaySuppressionLatched) return;
            launchAwaySuppressionLatched = false;
            applyLayerAlpha();
            View liveRoot = rootRef.get();
            if (liveRoot != null) liveRoot.postInvalidateOnAnimation();
        };
        hwuiProbe = BuildConfig.WORKSPACE_GLASS_HWUI_PROBE
                ? new LauncherGlassHwuiProbeView(context, session) : null;
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
                        cancelLaunchReturnFade();
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
        if (layer.hwuiProbe != null) {
            rootGroup.addView(layer.hwuiProbe, 1, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        BY_ROOT.put(root, layer);
        Long endAt = UNLOCK_END_BY_ROOT.get(root);
        if (endAt != null) layer.startMotionReveal(endAt);
        MainHook.log("[DC][LauncherGlass] shared static root layer attached root="
                + root.getClass().getSimpleName());
        return layer;
    }

    // Read-only package accessor for bounded folder diagnostics.
    static synchronized LauncherGlassStaticLayer find(View root) {
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
        if (layer.hwuiProbe != null) layer.hwuiProbe.postInvalidateOnAnimation();
        if (BuildConfig.WORKSPACE_GLASS_HWUI_PROBE && android.os.Trace.isEnabled()) {
            android.os.Trace.beginSection("LD.Workspace.HWUIProbe.ScrollMutation");
            try {
                android.os.Trace.setCounter("LD.Workspace.HWUIProbe.ScrollMutationCount",
                        HWUI_PROBE_SCROLL_MUTATIONS.incrementAndGet());
                android.os.Trace.setCounter("LD.Workspace.HWUIProbe.ScrollBeforeX", beforeScrollX);
                android.os.Trace.setCounter("LD.Workspace.HWUIProbe.ScrollAfterX", afterScrollX);
            } finally {
                android.os.Trace.endSection();
            }
        }
        layer.session.onWorkspaceScrollMutation(beforeScrollX, afterScrollX);
    }

    static boolean isHwuiProbeActive(View root) {
        LauncherGlassStaticLayer layer = find(root);
        return layer != null && layer.hwuiProbeActive;
    }

    static LauncherGlassHwuiProbeView.BackdropSnapshot snapshotHwuiProbeBackdrops(View root) {
        LauncherGlassStaticLayer layer = find(root);
        if (layer == null || !layer.hwuiProbeActive || layer.hwuiProbe == null) return null;
        return layer.hwuiProbe.snapshotBackdrops();
    }

    static boolean isWorkspaceGlassOutputHidden(View root) {
        LauncherGlassStaticLayer layer = find(root);
        return layer != null && layer.isOutputHidden();
    }

    static boolean isWorkspaceMotionAnimationActive(View root) {
        LauncherGlassStaticLayer layer = find(root);
        return layer != null && layer.isMotionAnimationActive();
    }

    private boolean isOutputHidden() {
        boolean hideDuringLaunch = launchProxyActive
                && (!launchProxyReturning || launchReturnAlpha <= 0.001f);
        return launchAwaySuppressionLatched || hideDuringLaunch
                || layerAlpha.alpha() * launchReturnAlpha <= 0.001f;
    }

    static void onLauncherResumed(View root) {
        LauncherGlassStaticLayer layer = find(root);
        if (layer == null || !layer.launchAwaySuppressionLatched) return;
        layer.mainHandler.removeCallbacks(layer.clearLaunchAwaySuppression);
        layer.mainHandler.postDelayed(layer.clearLaunchAwaySuppression, 220L);
    }

    /** Temporarily removes only Workspace glass from the popup's one-shot backdrop capture. */
    static boolean setPopupCaptureSuppressed(View root, boolean suppressed) {
        LauncherGlassStaticLayer layer = find(root);
        if (layer == null || layer.disposed) return false;
        Runnable update = () -> {
            if (layer.disposed || layer.popupCaptureSuppressed == suppressed) return;
            layer.popupCaptureSuppressed = suppressed;
            layer.applyLayerAlpha();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) update.run();
        else layer.mainHandler.post(update);
        return true;
    }

    private boolean isMotionAnimationActive() {
        return launchProxyActive
                || isAnimatorStarted(sceneAnimator)
                || isAnimatorStarted(motionAnimator)
                || isAnimatorStarted(launchReturnAnimator);
    }

    private static boolean isAnimatorStarted(ValueAnimator animator) {
        return animator != null && animator.isStarted();
    }

    /** Invalidate the active in-window probe without queueing a redundant static GL frame. */
    static boolean invalidateHwuiProbe(View root) {
        LauncherGlassStaticLayer layer = find(root);
        if (layer == null) return false;
        if (!layer.hwuiProbeActive || layer.hwuiProbe == null) return false;
        if (layer.isOutputHidden()) {
            // App-launch glass is hidden. Remember the newest wallpaper transform and apply it
            // when the return animation reveals the HWUI layer, without redrawing during launch.
            layer.hwuiProbeWallpaperScaleDirty = true;
            return true;
        }
        layer.hwuiProbeWallpaperScaleDirty = false;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            layer.hwuiProbe.invalidate();
        } else {
            layer.hwuiProbe.postInvalidateOnAnimation();
        }
        return true;
    }

    static void markHwuiProbeBackdropPending(View root) {
        LauncherGlassStaticLayer layer = find(root);
        if (layer == null || !layer.hwuiProbeActive) return;
        layer.hwuiProbeActive = false;
        layer.applyLayerAlpha();
        layer.session.requestStaticRedraw();
        MainHook.log("[DC][LauncherGlass] HWUI probe paused while backdrop geometry changes");
    }

    static boolean publishHwuiProbeBackdrop(
            View root, Bitmap rawBitmap, Bitmap blurredBitmap,
            int logicalWidth, int logicalHeight, int rotation,
            float capturedWallpaperScale) {
        LauncherGlassStaticLayer layer = find(root);
        if (layer == null || layer.hwuiProbe == null
                || rawBitmap == null || blurredBitmap == null) {
            if (rawBitmap != null) rawBitmap.recycle();
            if (blurredBitmap != null) blurredBitmap.recycle();
            return false;
        }
        if (!layer.hwuiProbe.setBackdrops(
                rawBitmap, blurredBitmap, logicalWidth, logicalHeight, rotation,
                capturedWallpaperScale)) {
            rawBitmap.recycle();
            blurredBitmap.recycle();
            return false;
        }
        layer.hwuiProbeActive = true;
        layer.hwuiProbeWallpaperScaleDirty = false;
        layer.applyLayerAlpha();
        MainHook.log("[DC][LauncherGlass] HWUI probe active; immutable PassBlur backdrop "
                + rawBitmap.getWidth() + "x" + rawBitmap.getHeight()
                + " blur=" + blurredBitmap.getWidth() + "x" + blurredBitmap.getHeight()
                + " rotation=" + rotation);
        return true;
    }

    static void failHwuiProbe(View root, LauncherGlassHwuiProbeView probe, Throwable error) {
        LauncherGlassStaticLayer layer = find(root);
        if (layer == null || layer.hwuiProbe != probe) return;
        layer.hwuiProbeActive = false;
        layer.applyLayerAlpha();
        MainHook.log("[DC][LauncherGlass] HWUI probe disabled; restored TextureView output: "
                + error);
        layer.session.requestStaticRedraw();
    }

    static Integer captureWorkspaceScrollAnchor(View root) {
        LauncherGlassStaticLayer layer = find(root);
        if (layer == null) return null;
        View workspace = layer.workspaceRef.get();
        if (workspace == null || !workspace.isAttachedToWindow()
                || workspace.getRootView() != root) return null;
        return workspace.getScrollX();
    }

    /** UI pre-draw applies native opacity to the cached output in the same traversal. */
    static float syncWorkspacePresentation(
            View root, View material, boolean launchProxyActive,
            boolean launchProxyReturning, float returnProgress) {
        LauncherGlassStaticLayer layer = find(root);
        if (layer == null) return 1f;
        View workspace = layer.workspaceRef.get();
        if (workspace == null || !workspace.isAttachedToWindow()
                || workspace.getRootView() != root) {
            workspace = LauncherGlassHierarchy.findWorkspaceRoot(material);
            layer.workspaceRef = new WeakReference<>(workspace);
        }
        // Before any Workspace node is registered there is no content to follow. Keep the
        // default factor so scene freshness and proxy ownership continue to decide presentation.
        // The native FolderCling opening animation can fade the Workspace
        // View hierarchy to zero. That is NOT a request to hide the already
        // frozen glass output (which is an independent Launcher-root sibling).
        // Keep other modes' opacity handling, including app and Recents.
        boolean frozenFolder = LauncherGlassSceneController
                .isFolderBackdropFrozenForRoot(root);
        float alpha = frozenFolder ? 1f : workspace != null
                ? LauncherGlassVisibility.alphaBelowRoot(workspace, root) : 1f;
        boolean wasReturning = layer.launchProxyActive && layer.launchProxyReturning;
        if (launchProxyReturning) {
            layer.mainHandler.removeCallbacks(layer.clearLaunchAwaySuppression);
            layer.launchAwaySuppressionLatched = false;
        } else if (launchProxyActive && !layer.launchAwaySuppressionLatched) {
            layer.launchAwaySuppressionLatched = true;
            layer.mainHandler.removeCallbacks(layer.clearLaunchAwaySuppression);
        }
        layer.layerAlpha.setWorkspaceAlpha(alpha, launchProxyActive);
        layer.launchProxyActive = launchProxyActive;
        layer.launchProxyReturning = launchProxyActive && launchProxyReturning;
        if (layer.launchProxyReturning) {
            long now = SystemClock.uptimeMillis();
            float progress = clampProgress(returnProgress);
            if (!wasReturning) layer.beginLaunchReturnCycle(progress, now);
            double remainingMs = layer.estimateLaunchReturnRemainingMs(progress, now);
            if (!layer.launchReturnFadeStarted
                    && remainingMs <= AnimationRuntimeState
                            .appReturnGlassFadeStartBeforeEndMs()) {
                layer.startLaunchReturnFade();
            }
        } else if (launchProxyActive) {
            layer.cancelLaunchReturnFade();
            layer.launchReturnStartedAtMs = 0L;
            layer.launchReturnStartProgress = 0f;
            layer.launchReturnLastProgress = 0f;
            layer.launchReturnAlpha = 0f;
        } else if (wasReturning) {
            // The native proxy disappearing is authoritative if progress never crossed the
            // configured fade-start threshold (for example, when a spring is interrupted).
            if (!layer.launchReturnFadeStarted) layer.startLaunchReturnFade();
            else if (layer.launchReturnAnimator == null) layer.launchReturnAlpha = 1f;
        } else if (layer.launchReturnAnimator == null) {
            layer.launchReturnAlpha = 1f;
        }
        layer.applyLayerAlpha();
        return alpha;
    }

    static void onNativeUnlockStarted(View root, long expectedDurationMs) {
        if (root == null || expectedDurationMs <= 0) return;
        long endAt = SystemClock.uptimeMillis() + expectedDurationMs;
        UNLOCK_END_BY_ROOT.put(root, endAt);
        LauncherGlassStaticLayer layer = find(root);
        if (layer == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) layer.startMotionReveal(endAt);
        else layer.mainHandler.post(() -> layer.startMotionReveal(endAt));
    }

    static void onNativeUnlockEnded(View root) {
        UNLOCK_END_BY_ROOT.remove(root);
        LauncherGlassStaticLayer layer = find(root);
        if (layer == null) return;
        Runnable finish = () -> {
            if (layer.disposed) return;
            layer.cancelMotionAnimation();
            layer.layerAlpha.setMotionAlpha(1f);
            layer.applyLayerAlpha();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) finish.run();
        else layer.mainHandler.post(finish);
    }

    private void startMotionReveal(long endAt) {
        if (disposed) return;
        cancelMotionAnimation();
        long remaining = Math.max(0L, endAt - SystemClock.uptimeMillis());
        long configured = AnimationRuntimeState.unlockGlassFadeDurationMs();
        long duration = LauncherGlassUnlockFadeTiming.lateFadeDurationMs(remaining, configured);
        long delay = duration > 0
                ? LauncherGlassUnlockFadeTiming.fadeDelayMs(remaining, duration) : 0L;
        boolean animate = layerAlpha.startMotionReveal(duration);
        applyLayerAlpha();
        if (!animate) return;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        motionAnimator = animator;
        animator.setDuration(duration);
        animator.setStartDelay(delay);
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
        MainHook.log("[DC][WorkspaceFade] native unlock reveal delayMs=" + delay
                + " durationMs=" + duration);
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
                LauncherGlassVisibilityTransition.plan(
                        layerAlpha.sceneAlpha(), visible,
                        AnimationRuntimeState.workspaceVisibilityDurationMs());
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

    private void applyLayerAlpha() {
        boolean hideDuringLaunch = launchAwaySuppressionLatched
                || (launchProxyActive
                    && (!launchProxyReturning || launchReturnAlpha <= 0.001f));
        float alpha = layerAlpha.alpha() * launchReturnAlpha;
        if (hideDuringLaunch || alpha <= 0.001f) {
            session.suppressWorkspaceGlassOutput();
        }
        float presentedAlpha = popupCaptureSuppressed || hideDuringLaunch ? 0f : alpha;
        if (hwuiProbeActive && hwuiProbe != null) {
            setAlpha(0f);
            hwuiProbe.setVisibility(hideDuringLaunch ? View.INVISIBLE : View.VISIBLE);
            hwuiProbe.setAlpha(presentedAlpha);
            if (presentedAlpha > 0.001f && hwuiProbeWallpaperScaleDirty) {
                hwuiProbeWallpaperScaleDirty = false;
                hwuiProbe.invalidate();
            }
        } else {
            setAlpha(presentedAlpha);
            if (hwuiProbe != null) hwuiProbe.setAlpha(0f);
        }
    }

    private void beginLaunchReturnCycle(float progress, long nowMs) {
        cancelLaunchReturnFade();
        launchReturnStartedAtMs = nowMs;
        launchReturnStartProgress = progress;
        launchReturnLastProgress = progress;
        launchReturnAlpha = 0f;
    }

    private double estimateLaunchReturnRemainingMs(float progress, long nowMs) {
        launchReturnLastProgress = Math.max(launchReturnLastProgress, clampProgress(progress));
        if (launchReturnLastProgress >= 0.999f) return 0d;
        long elapsedMs = Math.max(0L, nowMs - launchReturnStartedAtMs);
        float progressDelta = launchReturnLastProgress - launchReturnStartProgress;
        if (elapsedMs <= 0L || progressDelta <= 0.001f) return Double.POSITIVE_INFINITY;
        double remainingMs = (elapsedMs / (double) progressDelta)
                * (1d - launchReturnLastProgress);
        return Double.isFinite(remainingMs) ? Math.max(0d, remainingMs)
                : Double.POSITIVE_INFINITY;
    }

    private void startLaunchReturnFade() {
        if (launchReturnFadeStarted) return;
        launchReturnFadeStarted = true;
        long duration = AnimationRuntimeState.appReturnGlassFadeDurationMs();
        if (duration <= 0L) {
            launchReturnAlpha = 1f;
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        launchReturnAnimator = animator;
        animator.setDuration(duration);
        animator.setInterpolator(APP_RETURN_INTERPOLATOR);
        animator.addUpdateListener(value -> {
            if (disposed || launchReturnAnimator != value) return;
            launchReturnAlpha = (Float) value.getAnimatedValue();
            applyLayerAlpha();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (launchReturnAnimator != animation) return;
                launchReturnAnimator = null;
                launchReturnAlpha = 1f;
                applyLayerAlpha();
            }
        });
        animator.start();
    }

    private void cancelLaunchReturnFade() {
        ValueAnimator previous = launchReturnAnimator;
        launchReturnAnimator = null;
        launchReturnFadeStarted = false;
        if (previous != null) previous.cancel();
    }

    private static float clampProgress(float progress) {
        return Float.isFinite(progress) ? Math.max(0f, Math.min(1f, progress)) : 0f;
    }

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
        mainHandler.removeCallbacks(clearLaunchAwaySuppression);
        cancelSceneAnimation();
        cancelMotionAnimation();
        cancelLaunchReturnFade();
        View root = rootRef.get();
        if (root != null) {
            root.removeOnAttachStateChangeListener(rootAttachListener);
            forget(root, this);
        }
        if (hwuiProbe != null) {
            if (hwuiProbe.getParent() instanceof ViewGroup) {
                ((ViewGroup) hwuiProbe.getParent()).removeView(hwuiProbe);
            }
            hwuiProbe.clearBackdrops();
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
