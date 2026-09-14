package com.hellovoid.liquiddock;

import android.animation.ValueAnimator;
import android.graphics.Matrix;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.hellovoid.prismal.PrismalInteractionState;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Lightweight static Launcher glass binding. Owns no View, Surface, EGL surface or GPU resource. */
final class LauncherGlassStaticNode {
    private static final Map<View, WeakReference<LauncherGlassStaticNode>> BY_MATERIAL =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final WeakReference<View> materialRef;
    private final LauncherGlassDragState.Kind kind;
    private final LauncherGlassNodeKind nodeKind;
    private final LauncherGlassVisualOwnerState visualOwnerState =
            new LauncherGlassVisualOwnerState();
    private final float[] geometryPoints = new float[8];
    private final Matrix materialToGlobal = new Matrix();
    private final Matrix rootToGlobal = new Matrix();
    private final Matrix globalToRoot = new Matrix();
    private volatile LauncherGlassSession session;
    private final LiquidDockConfig.Glass glassConfig;
    private volatile float nativeCornerRadiusPx;
    private volatile boolean disposed;
    private final LauncherGlassSuppressionState suppressionState =
            new LauncherGlassSuppressionState();
    private final LauncherGlassPressState pressState = new LauncherGlassPressState();
    private ValueAnimator pressAnimator;
    private ValueAnimator visibilityAnimator;
    private volatile float visibilityAlpha;
    private final View.OnAttachStateChangeListener materialAttachListener;

    private LauncherGlassStaticNode(
            View materialHost,
            LauncherGlassDragState.Kind kind,
            LauncherGlassNodeKind nodeKind,
            LauncherGlassSession session,
            float cornerRadiusPx,
            LiquidDockConfig.Glass glassConfig) {
        materialRef = new WeakReference<>(materialHost);
        this.kind = kind != null ? kind : LauncherGlassDragState.Kind.FOLDER;
        this.nodeKind = nodeKind != null ? nodeKind : LauncherGlassNodeKind.LARGE_FOLDER;
        this.session = session;
        this.glassConfig = glassConfig;
        nativeCornerRadiusPx = Math.max(0f, cornerRadiusPx);
        materialAttachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                LauncherGlassSession live = ensureLiveSession();
                if (live != null) {
                    live.registerStaticNode(LauncherGlassStaticNode.this);
                    animateVisibilityTo(true);
                }
            }

            @Override public void onViewDetachedFromWindow(View v) {
                resetPressInteraction(false);
                hideImmediately();
                LauncherGlassSession live = session;
                if (live != null) live.unregisterStaticNode(LauncherGlassStaticNode.this);
            }
        };
        materialHost.addOnAttachStateChangeListener(materialAttachListener);
    }

    static LauncherGlassStaticNode attachToMaterial(
            View materialHost, float cornerRadiusPx, LiquidDockConfig.Glass glassConfig) {
        return attachToMaterial(materialHost, LauncherGlassDragState.Kind.FOLDER,
                cornerRadiusPx, glassConfig);
    }

    static LauncherGlassStaticNode attachToMaterial(
            View materialHost, LauncherGlassDragState.Kind kind,
            float cornerRadiusPx, LiquidDockConfig.Glass glassConfig) {
        if (materialHost == null) return null;
        LauncherGlassDragState.Kind resolvedKind = kind != null
                ? kind : LauncherGlassDragState.Kind.FOLDER;
        LauncherGlassNodeKind resolvedNodeKind = resolvedKind == LauncherGlassDragState.Kind.ICON
                ? LauncherGlassNodeKind.ICON
                : resolvedKind == LauncherGlassDragState.Kind.WIDGET
                ? LauncherGlassNodeKind.WIDGET : LauncherGlassNodeKind.LARGE_FOLDER;
        WeakReference<LauncherGlassStaticNode> reference = BY_MATERIAL.get(materialHost);
        LauncherGlassStaticNode existing = reference != null ? reference.get() : null;
        if (existing != null && !existing.disposed && existing.kind == resolvedKind
                && existing.nodeKind == resolvedNodeKind) {
            existing.setNativeCornerRadiusPx(cornerRadiusPx);
            LauncherGlassSession live = existing.ensureLiveSession();
            if (live != null) live.registerStaticNode(existing);
            return existing;
        }
        if (existing != null && !existing.disposed) existing.dispose();
        LauncherGlassSession shared = LauncherGlassSessionRegistry.acquire(materialHost, glassConfig);
        if (shared == null) return null;
        LauncherGlassStaticNode node = new LauncherGlassStaticNode(
                materialHost, resolvedKind, resolvedNodeKind, shared, cornerRadiusPx, glassConfig);
        BY_MATERIAL.put(materialHost, new WeakReference<>(node));
        shared.registerStaticNode(node);
        node.animateVisibilityTo(true);
        return node;
    }

    static LauncherGlassStaticNode attachFolderMaterial(
            View materialHost, boolean smallFolder, float cornerRadiusPx,
            LiquidDockConfig.Glass glassConfig) {
        if (materialHost == null) return null;
        LauncherGlassNodeKind resolvedNodeKind = smallFolder
                ? LauncherGlassNodeKind.SMALL_FOLDER : LauncherGlassNodeKind.LARGE_FOLDER;
        WeakReference<LauncherGlassStaticNode> reference = BY_MATERIAL.get(materialHost);
        LauncherGlassStaticNode existing = reference != null ? reference.get() : null;
        if (existing != null && !existing.disposed && existing.nodeKind == resolvedNodeKind) {
            existing.setNativeCornerRadiusPx(cornerRadiusPx);
            LauncherGlassSession live = existing.ensureLiveSession();
            if (live != null) live.registerStaticNode(existing);
            return existing;
        }
        if (existing != null && !existing.disposed) existing.dispose();
        LauncherGlassSession shared = LauncherGlassSessionRegistry.acquire(materialHost, glassConfig);
        if (shared == null) return null;
        LauncherGlassStaticNode node = new LauncherGlassStaticNode(materialHost,
                LauncherGlassDragState.Kind.FOLDER, resolvedNodeKind, shared,
                cornerRadiusPx, glassConfig);
        BY_MATERIAL.put(materialHost, new WeakReference<>(node));
        shared.registerStaticNode(node);
        node.animateVisibilityTo(true);
        return node;
    }

    static LauncherGlassStaticNode find(View materialHost) {
        if (materialHost == null) return null;
        WeakReference<LauncherGlassStaticNode> reference = BY_MATERIAL.get(materialHost);
        LauncherGlassStaticNode node = reference != null ? reference.get() : null;
        return node != null && !node.disposed ? node : null;
    }

    View materialHost() { return materialRef.get(); }
    LauncherGlassDragState.Kind kind() { return kind; }
    LauncherGlassNodeKind nodeKind() { return nodeKind; }

    GlassComponentStyle componentStyle() {
        GlassComponentStyle base;
        boolean liveEnabled;
        if (glassConfig == null) base = new GlassComponentStyle(true, 0f, 0f);
        else switch (nodeKind) {
            case ICON: base = glassConfig.iconStyle; break;
            case WIDGET: base = glassConfig.widgetStyle; break;
            case SMALL_FOLDER: base = glassConfig.smallFolderStyle; break;
            case LARGE_FOLDER:
            default: base = glassConfig.largeFolderStyle; break;
        }
        switch (nodeKind) {
            case ICON: liveEnabled = GlassRuntimeState.isIconEnabled(); break;
            case WIDGET: liveEnabled = GlassRuntimeState.isWidgetEnabled(); break;
            case SMALL_FOLDER: liveEnabled = GlassRuntimeState.isSmallFolderEnabled(); break;
            case LARGE_FOLDER:
            default: liveEnabled = GlassRuntimeState.isLargeFolderEnabled(); break;
        }
        return new GlassComponentStyle(liveEnabled, base.sizeOffsetDp, base.cornerRadiusDp);
    }

    void requestLifecycleRefresh() {
        if (disposed) return;
        LauncherGlassSession live = ensureLiveSession();
        View material = materialRef.get();
        View root = material != null ? material.getRootView() : null;
        if (root != null && root.isAttachedToWindow()) root.postInvalidateOnAnimation();
        if (live != null) live.requestStaticRedraw();
    }

    void setSuppressedByFolderOpen(boolean suppressed) {
        if (disposed || !suppressionState.setFolderOpen(suppressed)) return;
        if (suppressed) resetPressInteraction(false);
        animateVisibilityTo(!suppressionState.isSuppressed());
    }

    void setSuppressedByDrag(boolean suppressed) {
        if (disposed || !suppressionState.setDrag(suppressed)) return;
        if (suppressed) resetPressInteraction(false);
        animateVisibilityTo(!suppressionState.isSuppressed());
    }

    float visibilityAlpha() { return visibilityAlpha; }

    boolean retainLastGeometryDuringFade() {
        return visibilityAlpha > 0.001f && suppressionState.isSuppressed();
    }

    private void animateVisibilityTo(boolean visible) {
        if (disposed) return;
        if (visibilityAnimator != null) visibilityAnimator.cancel();
        LauncherGlassVisibilityTransition.Plan plan =
                LauncherGlassVisibilityTransition.plan(visibilityAlpha, visible);
        visibilityAlpha = plan.startAlpha;
        if (plan.durationMs == 0L) {
            visibilityAlpha = plan.targetAlpha;
            requestLifecycleRefresh();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(plan.startAlpha, plan.targetAlpha);
        visibilityAnimator = animator;
        animator.setDuration(plan.durationMs);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            if (visibilityAnimator != valueAnimator || disposed) return;
            visibilityAlpha = (Float) valueAnimator.getAnimatedValue();
            LauncherGlassSession live = session;
            if (live != null) live.requestStaticRedraw();
        });
        animator.start();
    }

    void hideImmediately() {
        if (visibilityAnimator != null) {
            visibilityAnimator.cancel();
            visibilityAnimator = null;
        }
        visibilityAlpha = 0f;
    }

    boolean holdLaunchProxyHidden() {
        if (disposed) return false;
        boolean wasActive = visualOwnerState.isLaunchProxyActive();
        if (!visualOwnerState.holdLaunchProxyHidden()) return false;
        if (!wasActive) resetPressInteraction(false);
        invalidateVisualOwnerGeometry();
        LauncherGlassSession live = session;
        if (live != null) live.requestStaticRedraw();
        return true;
    }

    boolean updateLaunchProxyGeometry(float left, float top, float right, float bottom) {
        if (disposed) return false;
        boolean wasActive = visualOwnerState.isLaunchProxyActive();
        boolean hadGeometry = visualOwnerState.copyLaunchProxyRect() != null;
        if (!visualOwnerState.updateLaunchProxyRect(
                new float[]{left, top, right, bottom})) return false;
        if (!wasActive) resetPressInteraction(false);
        invalidateVisualOwnerGeometry();
        LauncherGlassSession live = session;
        if (live != null) live.requestStaticRedraw();
        return !hadGeometry;
    }

    void endLaunchProxy() {
        if (disposed || !visualOwnerState.endLaunchProxy()) return;
        invalidateVisualOwnerGeometry();
        requestLifecycleRefresh();
    }

    private void invalidateVisualOwnerGeometry() {
        View material = materialRef.get();
        View root = material != null ? material.getRootView() : null;
        if (root != null && root.isAttachedToWindow()) root.postInvalidateOnAnimation();
    }

    void setNativeCornerRadiusPx(float cornerRadiusPx) {
        if (disposed || !Float.isFinite(cornerRadiusPx)) return;
        float next = Math.max(0f, cornerRadiusPx);
        if (Math.abs(nativeCornerRadiusPx - next) < 0.01f) return;
        nativeCornerRadiusPx = next;
        requestLifecycleRefresh();
    }

    void setPressInteraction(boolean pressed, float normalizedX, float normalizedY) {
        if (disposed) return;
        LauncherGlassPressState.Decision decision =
                pressState.setPressed(pressed, normalizedX, normalizedY);
        if (decision.animate) {
            animatePressTo(decision.targetProgress);
        } else if (decision.publishImmediately) {
            publishInteraction();
        }
    }

    void resetPressInteraction(boolean animated) {
        if (disposed) return;
        LauncherGlassPressState.Decision decision = pressState.reset(animated);
        if (decision.animate) {
            animatePressTo(decision.targetProgress);
            return;
        }
        if (pressAnimator != null) {
            pressAnimator.cancel();
            pressAnimator = null;
        }
        if (decision.publishImmediately) publishInteraction();
    }

    private void animatePressTo(float target) {
        if (pressAnimator != null) pressAnimator.cancel();
        float start = pressState.progress();
        if (Math.abs(start - target) < 0.001f) {
            pressState.setProgress(target);
            publishInteraction();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(start, target);
        pressAnimator = animator;
        animator.setDuration(target > start ? AnimationRuntimeState.pressInDurationMs()
                : AnimationRuntimeState.pressOutDurationMs());
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            if (pressAnimator != valueAnimator || disposed) return;
            pressState.setProgress((Float) valueAnimator.getAnimatedValue());
            publishInteraction();
        });
        animator.start();
    }

    private void publishInteraction() {
        LauncherGlassSession live = ensureLiveSession();
        if (disposed || live == null) return;
        live.updateStaticInteraction(this,
                new PrismalInteractionState(
                        pressState.progress(), pressState.glowCenterX(), pressState.glowCenterY()));
    }

    LauncherGlassGeometry.Snapshot captureGeometry(View root) {
        View material = materialRef.get();
        GlassComponentStyle style = componentStyle();
        if (disposed || material == null || root == null || style == null || !style.enabled
                || visibilityAlpha <= 0.001f) return null;
        int rootWidth = root.getWidth();
        int rootHeight = root.getHeight();
        if (rootWidth <= 0 || rootHeight <= 0) return null;

        int hostWidth = material.getWidth();
        int hostHeight = material.getHeight();
        float density = material.getResources().getDisplayMetrics().density;
        float requestedRadius = style.cornerRadiusDp > 0f
                ? style.cornerRadiusDp * density : nativeCornerRadiusPx;

        if (visualOwnerState.isLaunchProxyActive()) {
            // While MIUI owns the icon with FloatingIconView2/FloatingIconLayer2, the source View
            // is not the visual geometry authority. A null proxy rect intentionally means the vendor
            // proxy exists but its icon is still hidden; never expand glass over the task-sized rect.
            float[] proxyRect = visualOwnerState.copyLaunchProxyRect();
            if (proxyRect == null) return null;
            float proxyWidth = proxyRect[2] - proxyRect[0];
            float proxyHeight = proxyRect[3] - proxyRect[1];
            float referenceWidth = Math.max(1f, hostWidth);
            float referenceHeight = Math.max(1f, hostHeight);
            if (kind == LauncherGlassDragState.Kind.ICON) {
                LauncherGlassIconGeometry.Bounds icon = LauncherGlassIconGeometry.resolve(material);
                if (icon != null && icon.width() > 0f && icon.height() > 0f) {
                    referenceWidth = icon.width();
                    referenceHeight = icon.height();
                }
            }
            float radiusScale = Math.max(0.01f, Math.min(
                    proxyWidth / referenceWidth, proxyHeight / referenceHeight));
            return LauncherGlassGeometry.resolveStatic(
                    rootWidth, rootHeight,
                    proxyRect[0], proxyRect[1], proxyRect[2], proxyRect[3],
                    LauncherGlassBoundsPolicy.capRadius(
                            requestedRadius * radiusScale, proxyWidth, proxyHeight));
        }

        if (!LauncherGlassHierarchy.isWorkspace(material)
                || !LauncherGlassVisibility.isVisible(material, root)) return null;
        if (hostWidth <= 0 || hostHeight <= 0) return null;

        float localLeft = 0f;
        float localTop = 0f;
        float localRight = hostWidth;
        float localBottom = hostHeight;
        if (kind == LauncherGlassDragState.Kind.ICON) {
            LauncherGlassIconGeometry.Bounds icon = LauncherGlassIconGeometry.resolve(material);
            if (icon == null || icon.width() <= 0f || icon.height() <= 0f) return null;
            localLeft = icon.left;
            localTop = icon.top;
            localRight = icon.right;
            localBottom = icon.bottom;
        }
        float[] styledBounds = LauncherGlassBoundsPolicy.apply(
                localLeft, localTop, localRight, localBottom, style.sizeOffsetDp * density);
        localLeft = styledBounds[0];
        localTop = styledBounds[1];
        localRight = styledBounds[2];
        localBottom = styledBounds[3];
        float localWidth = Math.max(1f, localRight - localLeft);
        float localHeight = Math.max(1f, localBottom - localTop);
        geometryPoints[0] = localLeft;
        geometryPoints[1] = localTop;
        geometryPoints[2] = localRight;
        geometryPoints[3] = localTop;
        geometryPoints[4] = localLeft;
        geometryPoints[5] = localBottom;
        geometryPoints[6] = localRight;
        geometryPoints[7] = localBottom;
        materialToGlobal.reset();
        material.transformMatrixToGlobal(materialToGlobal);
        materialToGlobal.mapPoints(geometryPoints);
        rootToGlobal.reset();
        root.transformMatrixToGlobal(rootToGlobal);
        globalToRoot.reset();
        if (!rootToGlobal.invert(globalToRoot)) return null;
        globalToRoot.mapPoints(geometryPoints);

        float left = Math.min(Math.min(geometryPoints[0], geometryPoints[2]),
                Math.min(geometryPoints[4], geometryPoints[6]));
        float top = Math.min(Math.min(geometryPoints[1], geometryPoints[3]),
                Math.min(geometryPoints[5], geometryPoints[7]));
        float right = Math.max(Math.max(geometryPoints[0], geometryPoints[2]),
                Math.max(geometryPoints[4], geometryPoints[6]));
        float bottom = Math.max(Math.max(geometryPoints[1], geometryPoints[3]),
                Math.max(geometryPoints[5], geometryPoints[7]));
        float scaleX = distance(geometryPoints[0], geometryPoints[1],
                geometryPoints[2], geometryPoints[3]) / localWidth;
        float scaleY = distance(geometryPoints[0], geometryPoints[1],
                geometryPoints[4], geometryPoints[5]) / localHeight;
        float radiusScale = Math.max(0.01f, Math.min(scaleX, scaleY));
        return LauncherGlassGeometry.resolveStatic(
                root.getWidth(), root.getHeight(), left, top, right, bottom,
                LauncherGlassBoundsPolicy.capRadius(
                        requestedRadius * radiusScale, right - left, bottom - top));
    }

    void dispose() {
        if (disposed) return;
        resetPressInteraction(false);
        hideImmediately();
        disposed = true;
        View material = materialRef.get();
        if (material != null) {
            material.removeOnAttachStateChangeListener(materialAttachListener);
            WeakReference<LauncherGlassStaticNode> ref = BY_MATERIAL.get(material);
            if (ref != null && ref.get() == this) BY_MATERIAL.remove(material);
        }
        LauncherGlassSession live = session;
        if (live != null) live.unregisterStaticNode(this);
    }

    private LauncherGlassSession ensureLiveSession() {
        if (disposed) return null;
        if (!GlassRuntimeState.isEnabled() || !componentStyle().enabled) return null;
        View material = materialRef.get();
        if (!LauncherGlassHierarchy.isWorkspace(material)) return null;
        LauncherGlassSession current = session;
        View stableRoot = LauncherGlassSessionRegistry.resolveStableRoot(material);
        if (stableRoot == null) return null;
        if (current != null && !current.isShutdown() && current.ownsRoot(stableRoot)) return current;
        if (current != null) current.unregisterStaticNode(this);
        LauncherGlassSession replacement = LauncherGlassSessionRegistry.acquire(material, glassConfig);
        session = replacement;
        return replacement;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0.5f;
        return Math.max(0f, Math.min(1f, value));
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
