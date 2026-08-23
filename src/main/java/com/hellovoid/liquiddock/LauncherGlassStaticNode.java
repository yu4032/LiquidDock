package com.hellovoid.liquiddock;

import android.animation.ValueAnimator;
import android.graphics.Matrix;
import android.view.View;
import android.view.ViewParent;
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
    private static final long PRESS_IN_DURATION_MS = 90L;
    private static final long PRESS_OUT_DURATION_MS = 160L;

    private final WeakReference<View> materialRef;
    private final LauncherGlassDragState.Kind kind;
    private final LauncherGlassNodeKind nodeKind;
    private final LauncherGlassScrollMotionTracker workspaceScrollMotion =
            new LauncherGlassScrollMotionTracker();
    private final LauncherGlassEffectiveVisibilityTracker effectiveVisibilityTracker =
            new LauncherGlassEffectiveVisibilityTracker();
    private final LauncherGlassRootTransformTracker rootTransformMotion =
            new LauncherGlassRootTransformTracker();
    private final LauncherGlassVisualOwnerState visualOwnerState =
            new LauncherGlassVisualOwnerState();
    private WeakReference<View> workspaceRef = new WeakReference<>(null);
    private volatile LauncherGlassSession session;
    private final LiquidDockConfig.Glass glassConfig;
    private volatile float nativeCornerRadiusPx;
    private volatile boolean disposed;
    private volatile boolean suppressedByFolderOpen;
    private volatile boolean suppressedByDrag;
    private boolean geometryDirty = true;
    private boolean pressTarget;
    private float pressProgress;
    private float glowCenterX = 0.5f;
    private float glowCenterY = 0.5f;
    private ValueAnimator pressAnimator;
    private Object lastParent;
    private int lastLeft = Integer.MIN_VALUE;
    private int lastTop = Integer.MIN_VALUE;
    private int lastRight = Integer.MIN_VALUE;
    private int lastBottom = Integer.MIN_VALUE;
    private int lastVisibility = Integer.MIN_VALUE;
    private float lastAlpha = Float.NaN;
    private final float[] lastMatrix = new float[9];
    private boolean matrixInitialized;
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
                geometryDirty = true;
                LauncherGlassSession live = ensureLiveSession();
                if (live != null) live.registerStaticNode(LauncherGlassStaticNode.this);
            }

            @Override public void onViewDetachedFromWindow(View v) {
                resetPressInteraction(false);
                LauncherGlassSession live = session;
                if (live != null) live.unregisterStaticNode(LauncherGlassStaticNode.this);
                geometryDirty = true;
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
        if (glassConfig == null) return new GlassComponentStyle(true, 0f, 0f);
        switch (nodeKind) {
            case ICON: return glassConfig.iconStyle;
            case WIDGET: return glassConfig.widgetStyle;
            case SMALL_FOLDER: return glassConfig.smallFolderStyle;
            case LARGE_FOLDER:
            default: return glassConfig.largeFolderStyle;
        }
    }

    void requestLifecycleRefresh() {
        if (disposed) return;
        geometryDirty = true;
        LauncherGlassSession live = ensureLiveSession();
        if (live != null) live.requestStaticRedraw();
    }

    void setSuppressedByFolderOpen(boolean suppressed) {
        if (disposed || suppressedByFolderOpen == suppressed) return;
        if (suppressed) resetPressInteraction(false);
        suppressedByFolderOpen = suppressed;
        geometryDirty = true;
        requestLifecycleRefresh();
    }

    void setSuppressedByDrag(boolean suppressed) {
        if (disposed || suppressedByDrag == suppressed) return;
        if (suppressed) resetPressInteraction(false);
        suppressedByDrag = suppressed;
        geometryDirty = true;
        requestLifecycleRefresh();
    }

    boolean holdLaunchProxyHidden() {
        if (disposed) return false;
        boolean wasActive = visualOwnerState.isLaunchProxyActive();
        if (!visualOwnerState.holdLaunchProxyHidden()) return false;
        if (!wasActive) resetPressInteraction(false);
        geometryDirty = true;
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
        geometryDirty = true;
        invalidateVisualOwnerGeometry();
        LauncherGlassSession live = session;
        if (live != null) live.requestStaticRedraw();
        return !hadGeometry;
    }

    void endLaunchProxy() {
        if (disposed || !visualOwnerState.endLaunchProxy()) return;
        geometryDirty = true;
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
        geometryDirty = true;
        requestLifecycleRefresh();
    }

    void setPressInteraction(boolean pressed, float normalizedX, float normalizedY) {
        if (disposed) return;
        float nextX = clamp01(normalizedX);
        float nextY = clamp01(normalizedY);
        boolean centerChanged = glowCenterX != nextX || glowCenterY != nextY;
        glowCenterX = nextX;
        glowCenterY = nextY;
        if (pressTarget != pressed) {
            pressTarget = pressed;
            animatePressTo(pressed ? 1f : 0f);
        } else if (centerChanged) {
            publishInteraction();
        }
    }

    void resetPressInteraction(boolean animated) {
        if (disposed) return;
        pressTarget = false;
        if (animated && pressProgress > 0f) {
            animatePressTo(0f);
            return;
        }
        if (pressAnimator != null) {
            pressAnimator.cancel();
            pressAnimator = null;
        }
        pressProgress = 0f;
        glowCenterX = 0.5f;
        glowCenterY = 0.5f;
        publishInteraction();
    }

    private void animatePressTo(float target) {
        if (pressAnimator != null) pressAnimator.cancel();
        float start = pressProgress;
        if (Math.abs(start - target) < 0.001f) {
            pressProgress = target;
            publishInteraction();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(start, target);
        pressAnimator = animator;
        animator.setDuration(target > start ? PRESS_IN_DURATION_MS : PRESS_OUT_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            if (pressAnimator != valueAnimator || disposed) return;
            pressProgress = (Float) valueAnimator.getAnimatedValue();
            publishInteraction();
        });
        animator.start();
    }

    private void publishInteraction() {
        LauncherGlassSession live = ensureLiveSession();
        if (disposed || live == null) return;
        live.updateStaticInteraction(this,
                new PrismalInteractionState(pressProgress, glowCenterX, glowCenterY));
    }

    boolean syncFromMaterial() {
        View material = materialRef.get();
        if (disposed || material == null) return false;
        boolean changed = geometryDirty;
        geometryDirty = false;
        changed |= consumeWorkspaceScrollMotion();
        changed |= consumeRootSpaceTransformMotion(material);
        View sceneRoot = material.getRootView();
        changed |= effectiveVisibilityTracker.update(
                LauncherGlassVisibility.effectiveAlpha(material, sceneRoot));
        Object parent = material.getParent();
        if (lastParent != parent) { lastParent = parent; changed = true; }
        int left = material.getLeft();
        int top = material.getTop();
        int right = material.getRight();
        int bottom = material.getBottom();
        if (lastLeft != left || lastTop != top || lastRight != right || lastBottom != bottom) {
            lastLeft = left;
            lastTop = top;
            lastRight = right;
            lastBottom = bottom;
            changed = true;
        }
        int visibility = material.getVisibility();
        if (lastVisibility != visibility) { lastVisibility = visibility; changed = true; }
        float alpha = material.getAlpha();
        if (!Float.isFinite(lastAlpha) || Math.abs(lastAlpha - alpha) >= 0.01f) {
            lastAlpha = alpha;
            changed = true;
        }
        float[] matrix = new float[9];
        material.getMatrix().getValues(matrix);
        if (!matrixInitialized) {
            System.arraycopy(matrix, 0, lastMatrix, 0, matrix.length);
            matrixInitialized = true;
            changed = true;
        } else {
            for (int i = 0; i < matrix.length; i++) {
                if (Math.abs(lastMatrix[i] - matrix[i]) >= 0.001f) {
                    System.arraycopy(matrix, 0, lastMatrix, 0, matrix.length);
                    changed = true;
                    break;
                }
            }
        }
        return changed;
    }

    private boolean consumeRootSpaceTransformMotion(View material) {
        if (material == null || !material.isAttachedToWindow()) {
            return rootTransformMotion.update(null);
        }
        View root = material.getRootView();
        if (root == null || !root.isAttachedToWindow()) return rootTransformMotion.update(null);
        int width = material.getWidth();
        int height = material.getHeight();
        if (width <= 0 || height <= 0) return rootTransformMotion.update(null);
        float[] points = new float[]{
                0f, 0f,
                width, 0f,
                0f, height,
                width, height
        };
        Matrix materialGlobal = new Matrix();
        material.transformMatrixToGlobal(materialGlobal);
        materialGlobal.mapPoints(points);
        Matrix rootGlobal = new Matrix();
        root.transformMatrixToGlobal(rootGlobal);
        Matrix globalToRoot = new Matrix();
        if (!rootGlobal.invert(globalToRoot)) return rootTransformMotion.update(null);
        globalToRoot.mapPoints(points);
        return rootTransformMotion.update(points);
    }

    private boolean consumeWorkspaceScrollMotion() {
        View material = materialRef.get();
        View workspace = workspaceRef.get();
        if (workspace == null || !workspace.isAttachedToWindow()) {
            workspace = findWorkspaceAncestor(material);
            workspaceRef = new WeakReference<>(workspace);
        }
        if (workspace == null) return workspaceScrollMotion.update(null, 0, 0);
        return workspaceScrollMotion.update(workspace, workspace.getScrollX(), workspace.getScrollY());
    }

    private static View findWorkspaceAncestor(View material) {
        View cursor = material;
        while (cursor != null) {
            Class<?> type = cursor.getClass();
            if ("com.miui.home.launcher.Workspace".equals(type.getName())
                    || "Workspace".equals(type.getSimpleName())) return cursor;
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    LauncherGlassGeometry.Snapshot captureGeometry(View root) {
        View material = materialRef.get();
        GlassComponentStyle style = componentStyle();
        if (disposed || material == null || root == null || style == null || !style.enabled
                || suppressedByFolderOpen || suppressedByDrag) return null;
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
            return LauncherGlassGeometry.resolve(
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
        float[] points = new float[]{
                localLeft, localTop,
                localRight, localTop,
                localLeft, localBottom,
                localRight, localBottom
        };
        Matrix materialGlobal = new Matrix();
        material.transformMatrixToGlobal(materialGlobal);
        materialGlobal.mapPoints(points);
        Matrix rootGlobal = new Matrix();
        root.transformMatrixToGlobal(rootGlobal);
        Matrix globalToRoot = new Matrix();
        if (!rootGlobal.invert(globalToRoot)) return null;
        globalToRoot.mapPoints(points);

        float left = Math.min(Math.min(points[0], points[2]), Math.min(points[4], points[6]));
        float top = Math.min(Math.min(points[1], points[3]), Math.min(points[5], points[7]));
        float right = Math.max(Math.max(points[0], points[2]), Math.max(points[4], points[6]));
        float bottom = Math.max(Math.max(points[1], points[3]), Math.max(points[5], points[7]));
        float scaleX = distance(points[0], points[1], points[2], points[3]) / localWidth;
        float scaleY = distance(points[0], points[1], points[4], points[5]) / localHeight;
        float radiusScale = Math.max(0.01f, Math.min(scaleX, scaleY));
        return LauncherGlassGeometry.resolve(
                root.getWidth(), root.getHeight(), left, top, right, bottom,
                LauncherGlassBoundsPolicy.capRadius(
                        requestedRadius * radiusScale, right - left, bottom - top));
    }

    void dispose() {
        if (disposed) return;
        resetPressInteraction(false);
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
        if (!GlassRuntimeState.isEnabled()) return null;
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
