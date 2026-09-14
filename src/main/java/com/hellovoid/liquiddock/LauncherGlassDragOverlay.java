package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.os.Handler;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * One live upper-window drag presentation per stable Launcher root.
 *
 * <p>MIUI's DragView remains the logic authority inside Launcher. LiquidDock renders both the
 * glass and a draw-only mirror in an excluded application-panel window above Launcher, so the
 * PassBlur producer can continuously sample the real workspace without recursively capturing the
 * drag visual or the glass output itself.</p>
 */
final class LauncherGlassDragOverlay {
    private static final String TAG = "[DC][DragGlass]";
    private static final WeakHashMap<View, LauncherGlassDragOverlay> BY_ROOT = new WeakHashMap<>();

    private final WeakReference<View> rootRef;
    private final LiquidDockConfig.Glass glassConfig;
    private final LauncherGlassDragCoordinator coordinator = new LauncherGlassDragCoordinator();
    private final View carrier;
    private final Handler mainHandler;
    private final View.OnAttachStateChangeListener rootAttachListener;
    private final float[] sourcePoints = new float[8];
    private final float[] visualPoints = new float[8];
    private final Matrix sourceToGlobal = new Matrix();
    private final Matrix hostToGlobal = new Matrix();
    private final Matrix globalToHost = new Matrix();

    private WeakReference<View> sourceRef = new WeakReference<>(null);
    private LauncherGlassSinkView sink;
    private LauncherDragVisualMirror mirror;
    private LauncherDragSourceOverlay sourceOverlay;
    private LauncherGlassSession sourceSession;
    private boolean sourceAttachPending;
    private boolean presentationClaimed;
    private float vendorPresentationAlpha = 1f;
    private float activeCornerRadiusPx;
    private LauncherGlassNodeKind activeNodeKind = LauncherGlassNodeKind.LARGE_FOLDER;
    private float activeVisualLeft;
    private float activeVisualTop;
    private float activeVisualRight;
    private float activeVisualBottom;
    private boolean tracking;
    private boolean released;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!tracking || released) return;
            syncFromSource();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private LauncherGlassDragOverlay(View root, LiquidDockConfig.Glass glassConfig) {
        rootRef = new WeakReference<>(root);
        this.glassConfig = glassConfig;
        mainHandler = new Handler(root.getContext().getMainLooper());
        rootAttachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}

            @Override public void onViewDetachedFromWindow(View v) {
                mainHandler.post(() -> {
                    if (!v.isAttachedToWindow()) releaseRoot(v);
                });
            }
        };
        carrier = new View(root.getContext());
        carrier.setClickable(false);
        carrier.setFocusable(false);
        carrier.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        carrier.setVisibility(View.INVISIBLE);
        root.addOnAttachStateChangeListener(rootAttachListener);
    }

    /** Compatibility prewarm for the existing 4.50 createDragView hook; no snapshot is taken. */
    static LauncherGlassDragOverlay prepareCleanCapture(
            View source, LiquidDockConfig.Glass glassConfig) {
        if (!GlassRuntimeState.isEnabled() || source == null) return null;
        LauncherGlassDragOverlay overlay = acquire(source, glassConfig);
        if (overlay == null || overlay.released) return null;
        overlay.ensureWorkspaceSource();
        return overlay;
    }

    /** Compatibility no-op: live mode does not arm a frozen capture. */
    void armCleanCapture(View dragView) {}

    /** Compatibility no-op: vendor DragView stays visible until upper presentation is ready. */
    static void gateCleanDragPresentation(View dragView) {}

    /** Compatibility no-op: the dedicated drag producer stays live for the whole drag. */
    static void requestCleanBackdropAndReveal(View dragView) {}

    static boolean begin(
            View source,
            LiquidDockConfig.Glass glassConfig,
            Object token,
            LauncherGlassDragState.Kind kind,
            LauncherGlassNodeKind nodeKind,
            GlassComponentStyle style,
            float cornerRadiusPx,
            float[] visualBounds) {
        if (!GlassRuntimeState.isEnabled()) return false;
        LauncherGlassDragOverlay overlay = acquire(source, glassConfig);
        return overlay != null && overlay.beginInternal(
                token, kind, nodeKind, style, source, cornerRadiusPx, visualBounds);
    }

    static void end(View source, Object token) {
        LauncherGlassDragOverlay overlay = find(source, token);
        if (overlay != null) overlay.endInternal(token);
    }

    static void releaseAll() {
        LauncherGlassDragOverlay[] snapshot;
        synchronized (BY_ROOT) {
            snapshot = BY_ROOT.values().toArray(new LauncherGlassDragOverlay[0]);
        }
        for (LauncherGlassDragOverlay overlay : snapshot) {
            if (overlay == null) continue;
            View root = overlay.rootRef.get();
            if (root != null) overlay.releaseRoot(root);
        }
    }

    private static LauncherGlassDragOverlay acquire(
            View source, LiquidDockConfig.Glass glassConfig) {
        View root = LauncherGlassSessionRegistry.resolveStableRoot(source);
        if (root == null) return null;
        synchronized (BY_ROOT) {
            LauncherGlassDragOverlay current = BY_ROOT.get(root);
            if (current != null && !current.released) return current;
            LauncherGlassDragOverlay created = new LauncherGlassDragOverlay(root, glassConfig);
            BY_ROOT.put(root, created);
            return created;
        }
    }

    private static LauncherGlassDragOverlay find(View source, Object token) {
        View root = source != null ? source.getRootView() : null;
        synchronized (BY_ROOT) {
            LauncherGlassDragOverlay direct = root != null ? BY_ROOT.get(root) : null;
            if (direct != null && direct.owns(token)) return direct;
            for (LauncherGlassDragOverlay overlay : BY_ROOT.values()) {
                if (overlay != null && overlay.owns(token)) return overlay;
            }
        }
        return null;
    }

    private boolean beginInternal(
            Object token,
            LauncherGlassDragState.Kind kind,
            LauncherGlassNodeKind nodeKind,
            GlassComponentStyle style,
            View source,
            float cornerRadiusPx,
            float[] visualBounds) {
        if (released || token == null || source == null) return false;
        LauncherGlassDragState.Bounds bounds = readRootBounds(source);
        if (bounds == null) return false;
        if (style == null) style = new GlassComponentStyle(true, 0f, 0f);
        activeNodeKind = nodeKind != null ? nodeKind : LauncherGlassNodeKind.LARGE_FOLDER;
        float left = 0f;
        float top = 0f;
        float right = Math.max(1f, source.getWidth());
        float bottom = Math.max(1f, source.getHeight());
        if (visualBounds != null && visualBounds.length >= 4
                && Float.isFinite(visualBounds[0]) && Float.isFinite(visualBounds[1])
                && Float.isFinite(visualBounds[2]) && Float.isFinite(visualBounds[3])
                && visualBounds[2] > visualBounds[0] && visualBounds[3] > visualBounds[1]) {
            left = visualBounds[0];
            top = visualBounds[1];
            right = visualBounds[2];
            bottom = visualBounds[3];
        }
        float density = source.getResources().getDisplayMetrics().density;
        float[] styledBounds = LauncherGlassBoundsPolicy.apply(
                left, top, right, bottom, style.sizeOffsetDp * density);
        activeVisualLeft = styledBounds[0];
        activeVisualTop = styledBounds[1];
        activeVisualRight = styledBounds[2];
        activeVisualBottom = styledBounds[3];
        float resolvedRadiusPx = style.cornerRadiusDp > 0f
                ? style.cornerRadiusDp * density
                : Math.max(0f, Float.isFinite(cornerRadiusPx) ? cornerRadiusPx : 0f);
        activeCornerRadiusPx = LauncherGlassBoundsPolicy.capRadius(
                resolvedRadiusPx,
                activeVisualRight - activeVisualLeft,
                activeVisualBottom - activeVisualTop);
        sourceRef = new WeakReference<>(source);
        ensureWorkspaceSource();
        if (!coordinator.begin(token, kind, bounds, activeCornerRadiusPx)) return false;
        tracking = true;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        Choreographer.getInstance().postFrameCallback(frameCallback);
        syncFromSource();
        MainHook.log(TAG + " live begin kind=" + kind + " source="
                + source.getClass().getSimpleName());
        return true;
    }

    private void endInternal(Object token) {
        if (released || !coordinator.end(token)) return;
        tracking = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        restoreVendorPresentation("drag-end");
        sourceRef = new WeakReference<>(null);
        carrier.setVisibility(View.INVISIBLE);
        if (sink != null) sink.setVisibility(View.GONE);
        mainHandler.post(() -> {
            if (!tracking && !released) releaseDragSource();
        });
        MainHook.log(TAG + " live end");
    }

    private boolean owns(Object token) {
        if (released) return false;
        LauncherGlassDragState state = coordinator.current();
        return state != null && state.token == token;
    }

    private void syncFromSource() {
        if (!tracking || released) return;
        View source = sourceRef.get();
        View root = rootRef.get();
        if (source == null || root == null || !root.isAttachedToWindow()) return;
        LauncherGlassDragState state = coordinator.current();
        if (state == null) return;
        LauncherGlassDragState.Bounds bounds = readRootBounds(source);
        if (bounds == null) return;
        coordinator.update(state.token, bounds,
                source.getScaleX(), source.getRotation(), 1f);
        ensureWorkspaceSource();
        if (!ensureUpperPresentation(source)) return;
        applyCarrierGeometry(source);
        if (mirror != null && sourceOverlay != null) {
            mirror.syncFromDragView(source, sourceOverlay);
        }
        LauncherGlassSession session = sourceSession;
        if (session != null && !LauncherLiveDragSessionBridge.hasPreparedBackdrop(session)) {
            LauncherLiveDragSessionBridge.ensureLive(session);
        }
        claimVendorPresentation(source);
        if (presentationClaimed && source.getAlpha() != 0f) source.setAlpha(0f);
    }

    private boolean ensureUpperPresentation(View source) {
        if (released || source == null) return false;
        LauncherDragSourceOverlay overlay = sourceOverlay;
        LauncherGlassSession authority = sourceSession;
        if (overlay == null || authority == null || authority.isShutdown()
                || !overlay.isAttachedToWindow()) return false;

        ViewGroup glassHost = overlay.glassHost();
        if (carrier.getParent() != glassHost) {
            Object oldParent = carrier.getParent();
            if (oldParent instanceof ViewGroup) ((ViewGroup) oldParent).removeView(carrier);
            int width = Math.max(1, source.getWidth());
            int height = Math.max(1, source.getHeight());
            glassHost.addView(carrier, new ViewGroup.LayoutParams(width, height));
            carrier.layout(0, 0, width, height);
        }
        if (sink == null) {
            sink = LauncherGlassSinkView.attachToExternalMaterial(
                    carrier, authority, activeCornerRadiusPx, glassConfig);
            if (sink == null) return false;
            sink.setNodeKind(activeNodeKind);
        }
        if (mirror == null) {
            mirror = LauncherDragVisualMirror.attach(source, overlay.mirrorHost());
            if (mirror == null) return false;
        }
        return true;
    }

    private void ensureWorkspaceSource() {
        if (released || sourceAttachPending) return;
        if (sourceSession != null && !sourceSession.isShutdown()) return;
        View root = rootRef.get();
        if (root == null || !root.isAttachedToWindow() || root.getWindowToken() == null) return;
        sourceAttachPending = true;
        LauncherDragSourceOverlay created = LauncherDragSourceOverlay.attach(
                root, new LauncherDragSourceOverlay.Listener() {
                    @Override public void onAttached(LauncherDragSourceOverlay overlay) {
                        sourceAttachPending = false;
                        if (released) {
                            overlay.dispose();
                            return;
                        }
                        sourceOverlay = overlay;
                        LauncherGlassSession session = new LauncherGlassSession(
                                sourceOverlay, glassConfig,
                                PassBlurBindRequest.dragOverlay(sourceOverlay));
                        sourceSession = session;
                        LauncherLiveDragSessionBridge.ensureLive(session);
                        if (tracking) syncFromSource();
                    }

                    @Override public void onAttachFailed(Throwable error) {
                        sourceAttachPending = false;
                        restoreVendorPresentation("source-attach-failed");
                        MainHook.log(TAG + " upper live source attach failed: " + error);
                    }
                });
        if (created == null) sourceAttachPending = false;
        else sourceOverlay = created;
    }

    private void claimVendorPresentation(View source) {
        if (presentationClaimed || source == null || source != sourceRef.get()
                || mirror == null || sink == null || sourceSession == null) return;
        if (!mirror.isReadyForPresentation() || !sink.isAvailable()
                || !LauncherLiveDragSessionBridge.hasPreparedBackdrop(sourceSession)) return;
        vendorPresentationAlpha = source.getAlpha();
        mirror.showForPresentation(vendorPresentationAlpha);
        source.setAlpha(0f);
        source.invalidate();
        presentationClaimed = true;
        MainHook.log(TAG + " live upper presentation claimed");
    }

    private void restoreVendorPresentation(String reason) {
        View source = sourceRef.get();
        if (presentationClaimed && source != null) {
            source.setAlpha(vendorPresentationAlpha);
            source.invalidate();
        }
        if (mirror != null) mirror.hidePresentation();
        if (presentationClaimed) MainHook.log(TAG + " vendor presentation restored reason=" + reason);
        presentationClaimed = false;
        vendorPresentationAlpha = 1f;
    }

    private void releaseDragSource() {
        restoreVendorPresentation("release");
        if (mirror != null) {
            mirror.dispose();
            mirror = null;
        }
        if (sink != null) {
            sink.dispose();
            sink = null;
        }
        Object carrierParent = carrier.getParent();
        if (carrierParent instanceof ViewGroup) ((ViewGroup) carrierParent).removeView(carrier);
        LauncherGlassSession session = sourceSession;
        sourceSession = null;
        if (session != null) session.shutdown();
        LauncherDragSourceOverlay overlay = sourceOverlay;
        sourceOverlay = null;
        sourceAttachPending = false;
        if (overlay != null) overlay.dispose();
    }

    private void applyCarrierGeometry(View source) {
        LauncherDragSourceOverlay overlay = sourceOverlay;
        if (released || overlay == null || carrier.getParent() != overlay.glassHost()) return;
        LauncherGlassDragCarrierGeometry.Snapshot geometry =
                resolveCarrierGeometry(source, overlay.glassHost());
        if (geometry == null) return;

        int width = Math.max(1, (int) Math.ceil(geometry.carrierWidth()));
        int height = Math.max(1, (int) Math.ceil(geometry.carrierHeight()));
        ViewGroup.LayoutParams lp = carrier.getLayoutParams();
        if (lp != null && (lp.width != width || lp.height != height)) {
            lp.width = width;
            lp.height = height;
            carrier.setLayoutParams(lp);
        }

        carrier.setX(geometry.carrierLeft);
        carrier.setY(geometry.carrierTop);
        carrier.setPivotX(0f);
        carrier.setPivotY(0f);
        carrier.setScaleX(1f);
        carrier.setScaleY(1f);
        carrier.setRotation(0f);
        carrier.setAlpha(1f);
        carrier.setVisibility(View.VISIBLE);

        sink.setLocalVisualBounds(
                geometry.visualLeft, geometry.visualTop,
                geometry.visualRight, geometry.visualBottom);
        float originalVisualWidth = Math.max(1f, activeVisualRight - activeVisualLeft);
        float originalVisualHeight = Math.max(1f, activeVisualBottom - activeVisualTop);
        float radiusScale = Math.max(0.01f, Math.min(
                geometry.visualWidth() / originalVisualWidth,
                geometry.visualHeight() / originalVisualHeight));
        sink.setNativeCornerRadiusPx(LauncherGlassBoundsPolicy.capRadius(
                activeCornerRadiusPx * radiusScale,
                geometry.visualWidth(), geometry.visualHeight()));
        sink.syncFromMaterial();
        publishFrameGeometry();
    }

    private void publishFrameGeometry() {
        LauncherGlassSinkView liveSink = sink;
        LauncherGlassSession authority = sourceSession;
        View authorityRoot = sourceOverlay;
        if (liveSink == null || authority == null || authority.isShutdown()
                || authorityRoot == null || !authorityRoot.isAttachedToWindow()) return;
        LauncherGlassGeometry.Snapshot geometry = liveSink.captureGeometry(authorityRoot);
        if (geometry != null) authority.publishDragGeometry(liveSink, geometry);
    }

    private LauncherGlassDragCarrierGeometry.Snapshot resolveCarrierGeometry(
            View source, ViewGroup host) {
        if (source == null || host == null || !source.isAttachedToWindow()
                || !host.isAttachedToWindow() || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return null;
        }
        setRectCorners(sourcePoints, 0f, 0f, source.getWidth(), source.getHeight());
        setRectCorners(visualPoints,
                activeVisualLeft, activeVisualTop, activeVisualRight, activeVisualBottom);

        sourceToGlobal.reset();
        source.transformMatrixToGlobal(sourceToGlobal);
        sourceToGlobal.mapPoints(sourcePoints);
        sourceToGlobal.mapPoints(visualPoints);

        hostToGlobal.reset();
        host.transformMatrixToGlobal(hostToGlobal);
        globalToHost.reset();
        if (!hostToGlobal.invert(globalToHost)) return null;
        globalToHost.mapPoints(sourcePoints);
        globalToHost.mapPoints(visualPoints);

        return LauncherGlassDragCarrierGeometry.resolve(
                sourcePoints, visualPoints, host.getScrollX(), host.getScrollY());
    }

    private static void setRectCorners(
            float[] points, float left, float top, float right, float bottom) {
        points[0] = left;
        points[1] = top;
        points[2] = right;
        points[3] = top;
        points[4] = left;
        points[5] = bottom;
        points[6] = right;
        points[7] = bottom;
    }

    private LauncherGlassDragState.Bounds readRootBounds(View source) {
        View root = rootRef.get();
        if (released || root == null || source == null || !source.isAttachedToWindow()
                || source.getWidth() <= 0 || source.getHeight() <= 0) return null;
        int[] sourceScreen = new int[2];
        int[] rootScreen = new int[2];
        source.getLocationOnScreen(sourceScreen);
        root.getLocationOnScreen(rootScreen);
        float left = sourceScreen[0] - rootScreen[0];
        float top = sourceScreen[1] - rootScreen[1];
        return new LauncherGlassDragState.Bounds(
                left, top, left + source.getWidth(), top + source.getHeight());
    }

    private void releaseRoot(View root) {
        if (released) return;
        released = true;
        tracking = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        LauncherGlassDragState state = coordinator.current();
        if (state != null) coordinator.cancel(state.token);
        restoreVendorPresentation("root-detached");
        sourceRef = new WeakReference<>(null);

        synchronized (BY_ROOT) {
            if (BY_ROOT.get(root) == this) BY_ROOT.remove(root);
        }

        releaseDragSource();
        try { root.removeOnAttachStateChangeListener(rootAttachListener); }
        catch (Throwable ignored) {}
        MainHook.log(TAG + " released detached root");
    }
}
