package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.os.Handler;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * One persistent launcher drag-glass carrier per stable Launcher root.
 *
 * <p>The carrier is placed immediately below MIUI's DragContainer and owns exactly one
 * LauncherGlassSinkView. Folder, widget and icon adapters only feed source geometry into this
 * object; they never create their own drag TextureView or PassBlur producer.</p>
 */
final class LauncherGlassDragOverlay {
    private static final String TAG = "[DC][DragGlass]";
    private static final WeakHashMap<View, LauncherGlassDragOverlay> BY_ROOT = new WeakHashMap<>();
    private static final WeakHashMap<View, LauncherGlassDragOverlay> CLEAN_CAPTURE_BY_DRAG_VIEW =
            new WeakHashMap<>();

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
    private LauncherDragSourceOverlay sourceOverlay;
    private LauncherGlassSession sourceSession;
    private boolean sourceAttachPending;
    private WeakReference<View> cleanCaptureDragRef = new WeakReference<>(null);
    private boolean cleanCaptureRequested;
    private boolean cleanCaptureInFlight;
    private boolean cleanCapturePresentationGated;
    private float cleanCaptureOriginalAlpha = 1f;
    private WeakReference<ViewGroup> hostRef = new WeakReference<>(null);
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
                // Match LauncherGlassSession: allow a transient root reattach during the same
                // main-loop turn, but release the overlay tree if this root is actually gone.
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

    static LauncherGlassDragOverlay prepareCleanCapture(
            View source, LiquidDockConfig.Glass glassConfig) {
        if (!GlassRuntimeState.isEnabled() || source == null) return null;
        LauncherGlassDragOverlay overlay = acquire(source, glassConfig);
        if (overlay == null || overlay.released) return null;
        overlay.ensureWorkspaceSource();
        return overlay;
    }

    void armCleanCapture(View dragView) {
        if (released || dragView == null) return;
        cleanCaptureDragRef = new WeakReference<>(dragView);
        synchronized (CLEAN_CAPTURE_BY_DRAG_VIEW) {
            CLEAN_CAPTURE_BY_DRAG_VIEW.put(dragView, this);
        }
    }

    static void gateCleanDragPresentation(View dragView) {
        LauncherGlassDragOverlay overlay = cleanCaptureOwner(dragView);
        if (overlay == null || overlay.released || dragView == null) return;
        overlay.cleanCaptureOriginalAlpha = dragView.getAlpha();
        overlay.cleanCapturePresentationGated = true;
        dragView.setAlpha(0f);
    }

    static void requestCleanBackdropAndReveal(View dragView) {
        LauncherGlassDragOverlay overlay = cleanCaptureOwner(dragView);
        if (overlay != null) overlay.requestCleanBackdropAndRevealInternal(dragView);
    }

    private static LauncherGlassDragOverlay cleanCaptureOwner(View dragView) {
        if (dragView == null) return null;
        synchronized (CLEAN_CAPTURE_BY_DRAG_VIEW) {
            return CLEAN_CAPTURE_BY_DRAG_VIEW.get(dragView);
        }
    }

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
        if (sink != null) sink.setNodeKind(activeNodeKind);
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
        MainHook.log(TAG + " begin kind=" + kind + " source="
                + source.getClass().getSimpleName());
        return true;
    }

    private void endInternal(Object token) {
        if (released || !coordinator.end(token)) return;
        tracking = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        sourceRef = new WeakReference<>(null);
        carrier.setVisibility(View.INVISIBLE);
        if (sink != null) sink.setVisibility(View.GONE);
        mainHandler.post(() -> {
            if (!tracking && !released) releaseDragSource();
        });
        MainHook.log(TAG + " end");
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
        if (!ensureCarrier(source)) return;
        applyCarrierGeometry(source);
    }

    private boolean ensureCarrier(View source) {
        if (released) return false;
        if (carrier.getParent() == null) {
            View dragContainer = findDragContainerAncestor(source);
            if (dragContainer == null || !(dragContainer.getParent() instanceof ViewGroup)) {
                return false;
            }
            ViewGroup host = (ViewGroup) dragContainer.getParent();
            int index = Math.max(0, host.indexOfChild(dragContainer));
            int width = Math.max(1, source.getWidth());
            int height = Math.max(1, source.getHeight());
            host.addView(carrier, index, new ViewGroup.LayoutParams(width, height));
            carrier.layout(0, 0, width, height);
            hostRef = new WeakReference<>(host);
            MainHook.log(TAG + " attached below " + dragContainer.getClass().getSimpleName());
        }
        if (sink == null) {
            if (!carrier.isAttachedToWindow() || carrier.getWidth() <= 0 || carrier.getHeight() <= 0) {
                return false;
            }
            LauncherGlassSession authority = sourceSession;
            if (authority == null || authority.isShutdown()) {
                ensureWorkspaceSource();
                return false;
            }
            sink = LauncherGlassSinkView.attachToExternalMaterial(
                    carrier, authority, activeCornerRadiusPx, glassConfig);
            if (sink == null) return false;
            sink.setNodeKind(activeNodeKind);
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
                        View pendingDrag = cleanCaptureDragRef.get();
                        if (cleanCaptureRequested && pendingDrag != null) {
                            requestCleanBackdropAndRevealInternal(pendingDrag);
                        } else if (tracking) {
                            session.freezeAfterNextFreshFrame();
                        }
                        if (tracking) syncFromSource();
                    }

                    @Override public void onAttachFailed(Throwable error) {
                        sourceAttachPending = false;
                        MainHook.log(TAG + " upper workspace source attach failed: " + error);
                    }
                });
        if (created == null) sourceAttachPending = false;
        else sourceOverlay = created;
    }

    private void requestCleanBackdropAndRevealInternal(View dragView) {
        if (released || dragView == null || cleanCaptureDragRef.get() != dragView) return;
        cleanCaptureRequested = true;
        ensureWorkspaceSource();
        LauncherGlassSession session = sourceSession;
        if (session == null || session.isShutdown() || cleanCaptureInFlight) return;
        cleanCaptureInFlight = true;
        session.freezeAfterNextFreshFrame(
                () -> restoreCleanDragPresentation(dragView, "fresh-workspace"),
                () -> restoreCleanDragPresentation(dragView, "source-failure"));
    }

    private void restoreCleanDragPresentation(View dragView, String reason) {
        if (dragView == null || cleanCaptureDragRef.get() != dragView) return;
        cleanCaptureInFlight = false;
        cleanCaptureRequested = false;
        if (cleanCapturePresentationGated) {
            dragView.setAlpha(cleanCaptureOriginalAlpha);
            dragView.invalidate();
        }
        cleanCapturePresentationGated = false;
        synchronized (CLEAN_CAPTURE_BY_DRAG_VIEW) {
            if (CLEAN_CAPTURE_BY_DRAG_VIEW.get(dragView) == this) {
                CLEAN_CAPTURE_BY_DRAG_VIEW.remove(dragView);
            }
        }
        cleanCaptureDragRef = new WeakReference<>(null);
        MainHook.log(TAG + " clean workspace capture released presentation reason=" + reason);
    }

    private void releaseDragSource() {
        View cleanDrag = cleanCaptureDragRef.get();
        if (cleanDrag != null && cleanCapturePresentationGated) {
            restoreCleanDragPresentation(cleanDrag, "release");
        }
        if (sink != null) {
            sink.dispose();
            sink = null;
        }
        LauncherGlassSession session = sourceSession;
        sourceSession = null;
        if (session != null) session.shutdown();
        LauncherDragSourceOverlay overlay = sourceOverlay;
        sourceOverlay = null;
        sourceAttachPending = false;
        if (overlay != null) overlay.dispose();
    }

    private void applyCarrierGeometry(View source) {
        ViewGroup host = hostRef.get();
        if (released || host == null || carrier.getParent() != host) return;
        LauncherGlassDragCarrierGeometry.Snapshot geometry = resolveCarrierGeometry(source, host);
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
        // sourceToGlobal already contains DragView and ancestor transforms. The carrier is a
        // sibling below DragContainer, so reapplying source transform here would double scale/
        // pivot displacement and would miss ancestor transforms. Keep the carrier in final host
        // space with an identity local transform.
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
        sourceRef = new WeakReference<>(null);

        synchronized (BY_ROOT) {
            if (BY_ROOT.get(root) == this) BY_ROOT.remove(root);
        }

        releaseDragSource();
        Object parent = carrier.getParent();
        if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(carrier);
        hostRef = new WeakReference<>(null);
        try { root.removeOnAttachStateChangeListener(rootAttachListener); }
        catch (Throwable ignored) {}
        MainHook.log(TAG + " released detached root");
    }

    private static View findDragContainerAncestor(View source) {
        Object cursor = source;
        while (cursor instanceof View) {
            ViewParent parent = ((View) cursor).getParent();
            if (parent == null) return null;
            if (parent instanceof View
                    && parent.getClass().getName().contains("DragContainer")) {
                return (View) parent;
            }
            cursor = parent;
        }
        return null;
    }
}
