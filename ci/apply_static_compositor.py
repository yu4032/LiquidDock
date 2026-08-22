from pathlib import Path
import re

ROOT = Path('.')
MAIN = ROOT / 'src/main/java/com/hellovoid/liquiddock'
TEST = ROOT / 'src/test/java/com/hellovoid/liquiddock'


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{label}: expected exactly one occurrence, got {count}')
    return text.replace(old, new, 1)


def replace_section(text, start, end, replacement, label):
    a = text.find(start)
    if a < 0:
        raise RuntimeError(f'{label}: start marker missing')
    b = text.find(end, a)
    if b < 0:
        raise RuntimeError(f'{label}: end marker missing')
    return text[:a] + replacement + '\n\n' + text[b:]


static_node = r'''package com.hellovoid.liquiddock;

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

/** Lightweight static Launcher glass binding. Owns no View, Surface, EGLSurface or GPU resource. */
final class LauncherGlassStaticNode {
    private static final Map<View, WeakReference<LauncherGlassStaticNode>> BY_MATERIAL =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final long PRESS_IN_DURATION_MS = 90L;
    private static final long PRESS_OUT_DURATION_MS = 160L;

    private final WeakReference<View> materialRef;
    private final LauncherGlassScrollMotionTracker workspaceScrollMotion =
            new LauncherGlassScrollMotionTracker();
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
            LauncherGlassSession session,
            float cornerRadiusPx,
            LiquidDockConfig.Glass glassConfig) {
        materialRef = new WeakReference<>(materialHost);
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
        if (materialHost == null) return null;
        WeakReference<LauncherGlassStaticNode> reference = BY_MATERIAL.get(materialHost);
        LauncherGlassStaticNode existing = reference != null ? reference.get() : null;
        if (existing != null && !existing.disposed) {
            existing.setNativeCornerRadiusPx(cornerRadiusPx);
            LauncherGlassSession live = existing.ensureLiveSession();
            if (live != null) live.registerStaticNode(existing);
            return existing;
        }
        LauncherGlassSession shared = LauncherGlassSessionRegistry.acquire(materialHost, glassConfig);
        if (shared == null) return null;
        LauncherGlassStaticNode node = new LauncherGlassStaticNode(
                materialHost, shared, cornerRadiusPx, glassConfig);
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

    void requestLifecycleRefresh() {
        if (disposed) return;
        geometryDirty = true;
        LauncherGlassSession live = ensureLiveSession();
        if (live != null) live.requestLifecycleRefresh();
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
        if (disposed || material == null || root == null
                || suppressedByFolderOpen || suppressedByDrag
                || !material.isAttachedToWindow() || !material.isShown()
                || material.getAlpha() <= 0f) return null;
        int width = material.getWidth();
        int height = material.getHeight();
        if (width <= 0 || height <= 0 || root.getWidth() <= 0 || root.getHeight() <= 0) return null;

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
        if (!rootGlobal.invert(globalToRoot)) return null;
        globalToRoot.mapPoints(points);

        float left = Math.min(Math.min(points[0], points[2]), Math.min(points[4], points[6]));
        float top = Math.min(Math.min(points[1], points[3]), Math.min(points[5], points[7]));
        float right = Math.max(Math.max(points[0], points[2]), Math.max(points[4], points[6]));
        float bottom = Math.max(Math.max(points[1], points[3]), Math.max(points[5], points[7]));
        float scaleX = distance(points[0], points[1], points[2], points[3]) / Math.max(1f, width);
        float scaleY = distance(points[0], points[1], points[4], points[5]) / Math.max(1f, height);
        float radiusScale = Math.max(0.01f, Math.min(scaleX, scaleY));
        return LauncherGlassGeometry.resolve(
                root.getWidth(), root.getHeight(), left, top, right, bottom,
                nativeCornerRadiusPx * radiusScale);
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
        View material = materialRef.get();
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
'''

static_layer = r'''package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** One transparent static Launcher glass output for an entire stable Launcher root. */
final class LauncherGlassStaticLayer extends TextureView implements TextureView.SurfaceTextureListener {
    private static final WeakHashMap<View, LauncherGlassStaticLayer> BY_ROOT = new WeakHashMap<>();

    private final WeakReference<View> rootRef;
    private final LauncherGlassSession session;
    private final Handler mainHandler;
    private Surface outputSurface;
    private boolean disposed;
    private final View.OnAttachStateChangeListener rootAttachListener;

    private LauncherGlassStaticLayer(Context context, View root, LauncherGlassSession session) {
        super(context);
        rootRef = new WeakReference<>(root);
        this.session = session;
        mainHandler = new Handler(context.getMainLooper());
        setOpaque(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSurfaceTextureListener(this);
        rootAttachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}

            @Override public void onViewDetachedFromWindow(View v) {
                mainHandler.post(() -> {
                    View stableRoot = rootRef.get();
                    if (stableRoot == v && !v.isAttachedToWindow()) forget(stableRoot, LauncherGlassStaticLayer.this);
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
        root.addView(layer, 0, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        BY_ROOT.put(root, layer);
        MainHook.log("[DC][LauncherGlass] shared static root layer attached root="
                + root.getClass().getSimpleName());
        return layer;
    }

    private static synchronized void forget(View root, LauncherGlassStaticLayer layer) {
        if (root != null && BY_ROOT.get(root) == layer) BY_ROOT.remove(root);
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
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
'''

(MAIN / 'LauncherGlassStaticNode.java').write_text(static_node)
(MAIN / 'LauncherGlassStaticLayer.java').write_text(static_layer)

# Folder hook: lightweight static nodes replace per-folder TextureViews.
folder_path = MAIN / 'MiuixFolderGlassHook.java'
folder = folder_path.read_text()
folder = folder.replace('LauncherGlassSinkView', 'LauncherGlassStaticNode')
folder = replace_section(
    folder,
    '    private static LauncherGlassStaticNode attachMaterial(\n',
    '    private static LauncherGlassStaticNode claimedSink(View material) {',
    r'''    private static LauncherGlassStaticNode attachMaterial(
            View material, LiquidDockConfig.Glass glassConfig) {
        if (material == null) return null;
        LauncherGlassStaticNode existing = claimedSink(material);
        if (existing != null) {
            clearVendorBlur(material);
            makeMaterialTransparent(material);
            MiuixLauncherDragOverlayHook.observeStaticNode(existing);
            return existing;
        }
        float nativeRadius = readMaterialRadius(material);
        float fallbackRadius = Math.min(Math.max(1, material.getWidth()),
                Math.max(1, material.getHeight())) * 0.22f;
        float density = material.getResources().getDisplayMetrics().density;
        float radius = LauncherGlassCornerRadiusPolicy.resolve(
                glassConfig != null ? glassConfig.folderCornerRadiusDp : 0f,
                density, nativeRadius, fallbackRadius);
        LauncherGlassStaticNode sink = LauncherGlassStaticNode.attachToMaterial(
                material, radius, glassConfig);
        if (sink != null) {
            CLAIMED.put(material, new WeakReference<>(sink));
            MiuixLauncherDragOverlayHook.observeStaticNode(sink);
            clearVendorBlur(material);
            makeMaterialTransparent(material);
            MainHook.log(TAG + " FolderIcon material joined shared static launcher compositor");
        }
        return sink;
    }''',
    'folder attachMaterial')
folder_path.write_text(folder)

# Drag hook: keep the one drag sink, but resolve/suppress the lightweight static counterpart.
drag_path = MAIN / 'MiuixLauncherDragOverlayHook.java'
drag = drag_path.read_text()
drag = drag.replace('LauncherGlassSinkView', 'LauncherGlassStaticNode')
drag = drag.replace(
'''                    if (child instanceof LauncherGlassStaticNode) {
                        installStaticSinkDragSuppression((LauncherGlassStaticNode) child);
                    }
''', '')
drag = drag.replace('installStaticSinkDragSuppression', 'installStaticNodeDragSuppression')
drag = drag.replace('findStaticSink(metadata.folderMaterial)', 'LauncherGlassStaticNode.find(metadata.folderMaterial)')
drag = drag.replace('findStaticSink((View) target)', 'LauncherGlassStaticNode.find((View) target)')
drag = replace_section(
    drag,
    '    private static LauncherGlassStaticNode findStaticSink(View material) {',
    '    private static float resolveCornerRadius(',
    '',
    'remove parent-scan static sink lookup')
marker = '    private static void installStaticNodeDragSuppression(LauncherGlassStaticNode sink) {'
observe = '''    // Drag overlay itself still owns one LauncherGlassSinkView; static desktop nodes own no View.\n    static void observeStaticNode(LauncherGlassStaticNode node) {\n        installStaticNodeDragSuppression(node);\n    }\n\n'''
drag = replace_once(drag, marker, observe + marker, 'insert static node drag observer')
drag_path.write_text(drag)

# Session: retain drag sinks, add one root static output + lightweight static nodes.
session_path = MAIN / 'LauncherGlassSession.java'
session = session_path.read_text()
static_state = r'''
    private static final class StaticNodeState {
        final WeakReference<LauncherGlassStaticNode> nodeRef;
        final LauncherGlassGeometryStability geometryStability =
                new LauncherGlassGeometryStability();
        volatile LauncherGlassGeometry.Snapshot geometry;
        volatile PrismalInteractionState interaction = PrismalInteractionState.IDLE;

        StaticNodeState(LauncherGlassStaticNode node) {
            nodeRef = new WeakReference<>(node);
        }
    }

'''
session = replace_once(session,
        '    private static final class OutputState {',
        static_state + '    private static final class OutputState {',
        'StaticNodeState insertion')
session = replace_once(session,
'''    private final Map<LauncherGlassSinkView, NodeState> nodes =
            Collections.synchronizedMap(new WeakHashMap<>());
    // EGL surfaces are created/destroyed only on renderHandler.
    private final Map<LauncherGlassSinkView, OutputState> outputs = new WeakHashMap<>();
''',
'''    private final Map<LauncherGlassSinkView, NodeState> nodes =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<LauncherGlassStaticNode, StaticNodeState> staticNodes =
            Collections.synchronizedMap(new WeakHashMap<>());
    // EGL surfaces are created/destroyed only on renderHandler.
    private final Map<LauncherGlassSinkView, OutputState> outputs = new WeakHashMap<>();
    private OutputState staticOutput;
''', 'session static fields')

static_api = r'''
    void registerStaticNode(LauncherGlassStaticNode node) {
        if (node == null || shuttingDown) return;
        synchronized (staticNodes) {
            if (!staticNodes.containsKey(node)) staticNodes.put(node, new StaticNodeState(node));
        }
        View root = rootRef.get();
        if (root != null) {
            mainHandler.post(() -> {
                if (!shuttingDown && ownsRoot(root)) LauncherGlassStaticLayer.acquire(root, this);
            });
        }
        syncSceneOnUiThread();
        requestLifecycleRefresh();
    }

    void unregisterStaticNode(LauncherGlassStaticNode node) {
        if (node == null) return;
        synchronized (staticNodes) { staticNodes.remove(node); }
        requestLifecycleRefresh();
    }

    void updateStaticInteraction(
            LauncherGlassStaticNode node, PrismalInteractionState interaction) {
        if (node == null || shuttingDown) return;
        synchronized (staticNodes) {
            StaticNodeState state = staticNodes.get(node);
            if (state == null) return;
            state.interaction = interaction != null ? interaction : PrismalInteractionState.IDLE;
        }
        requestFrame(false);
    }

'''
session = replace_once(session,
        '    void requestLifecycleRefresh() {',
        static_api + '    void requestLifecycleRefresh() {',
        'static node API insertion')

static_output_api = r'''
    void attachStaticOutput(Surface surface, int width, int height) {
        if (surface == null) return;
        if (shuttingDown || !renderThread.isAlive()) {
            surface.release();
            return;
        }
        postRender(() -> {
            if (shuttingDown) {
                surface.release();
                return;
            }
            try {
                ensureEglAndGl();
                releaseOutput(staticOutput);
                OutputState next = new OutputState(surface, width, height);
                next.eglSurface = EGL14.eglCreateWindowSurface(
                        eglDisplay, eglConfig, surface, new int[]{EGL14.EGL_NONE}, 0);
                checkEgl("eglCreateWindowSurface(static)", next.eglSurface != EGL14.EGL_NO_SURFACE);
                staticOutput = next;
                requestFrame(false);
            } catch (Throwable error) {
                MainHook.log(TAG + " attach static output failed " + debugLabel() + ": " + error);
                surface.release();
            }
        }, surface::release);
    }

    void resizeStaticOutput(int width, int height) {
        if (shuttingDown || !renderThread.isAlive()) return;
        postRender(() -> {
            OutputState output = staticOutput;
            if (output != null) {
                output.width = Math.max(1, width);
                output.height = Math.max(1, height);
                requestFrame(false);
            }
        }, null);
    }

    void detachStaticOutput(Surface surface) {
        if (shuttingDown || !renderThread.isAlive()) {
            if (surface != null) surface.release();
            return;
        }
        postRender(() -> {
            OutputState output = staticOutput;
            staticOutput = null;
            if (output != null) releaseOutput(output);
            else if (surface != null) surface.release();
        }, () -> { if (surface != null) surface.release(); });
    }

'''
session = replace_once(session,
        '    private void installRootObserver() {',
        static_output_api + '    private void installRootObserver() {',
        'static output API insertion')

new_sync = r'''    private void syncSceneOnUiThread() {
        if (shuttingDown) return;
        View root = rootRef.get();
        if (root == null) return;
        int nextWidth = root.getWidth();
        int nextHeight = root.getHeight();
        boolean rootGeometryChanged = nextWidth > 0 && nextHeight > 0
                && (nextWidth != rootWidth || nextHeight != rootHeight);
        boolean changed = rootGeometryChanged;
        if (nextWidth > 0) rootWidth = nextWidth;
        if (nextHeight > 0) rootHeight = nextHeight;

        List<NodeState> dragSnapshot;
        synchronized (nodes) { dragSnapshot = new ArrayList<>(nodes.values()); }
        for (NodeState node : dragSnapshot) {
            LauncherGlassSinkView sink = node.sinkRef.get();
            if (sink == null) continue;
            boolean localChanged = sink.syncFromMaterial();
            changed |= localChanged;
            LauncherGlassGeometry.Snapshot observed = sink.captureGeometry(root);
            LauncherGlassGeometry.Snapshot old = node.geometry;
            LauncherGlassGeometry.Snapshot selected =
                    node.geometryStability.select(old, observed, localChanged);
            if ((old == null) != (selected == null)
                    || (old != null && !old.sameAs(selected))) {
                node.geometry = selected;
                changed = true;
            }
        }

        List<StaticNodeState> staticSnapshot;
        synchronized (staticNodes) { staticSnapshot = new ArrayList<>(staticNodes.values()); }
        for (StaticNodeState state : staticSnapshot) {
            LauncherGlassStaticNode node = state.nodeRef.get();
            if (node == null) continue;
            boolean localChanged = node.syncFromMaterial();
            changed |= localChanged;
            LauncherGlassGeometry.Snapshot observed = node.captureGeometry(root);
            LauncherGlassGeometry.Snapshot old = state.geometry;
            LauncherGlassGeometry.Snapshot selected =
                    state.geometryStability.select(old, observed, localChanged);
            if ((old == null) != (selected == null)
                    || (old != null && !old.sameAs(selected))) {
                state.geometry = selected;
                changed = true;
            }
        }

        boolean producerGeometryChanged = refreshProducerGeometryOnUi(root);
        if (producerGeometryChanged || rootGeometryChanged) {
            requestBackdropRebuild();
        } else if (changed) {
            requestFrame(false);
        }
    }'''
session = replace_section(
        session,
        '    private void syncSceneOnUiThread() {',
        '    private boolean rebuildAtlasLayout(View root) {',
        new_sync,
        'session scene sync')

new_render = r'''    private void renderScene(boolean rebuildBackdrop) {
        PrismalParams params = prismalParams;
        if (params == null || rootWidth <= 0 || rootHeight <= 0
                || (staticOutput == null && outputs.isEmpty())) return;
        makePbufferCurrent();
        boolean rawTargetChanged = rawFramebuffer == 0
                || rawWidth != rootWidth || rawHeight != rootHeight;
        ensureRawTarget(rootWidth, rootHeight);
        if (rebuildBackdrop || rawTargetChanged || !backdropPrepared) {
            renderNormalizationRoot();
            prismalRenderer.prepareBackdrop(rawTexture, rootWidth, rootHeight, params);
            backdropPrepared = true;
        }
        renderStaticScene(params);
        renderDragOutputs(params);
    }

    private void renderStaticScene(PrismalParams params) {
        OutputState output = staticOutput;
        if (output == null || output.eglSurface == EGL14.EGL_NO_SURFACE) return;
        makePbufferCurrent();
        prismalRenderer.beginGlassFrame();
        List<StaticNodeState> snapshot;
        synchronized (staticNodes) { snapshot = new ArrayList<>(staticNodes.values()); }
        for (StaticNodeState state : snapshot) {
            LauncherGlassStaticNode node = state.nodeRef.get();
            LauncherGlassGeometry.Snapshot geometry = state.geometry;
            if (node == null || geometry == null) continue;
            PrismalGeometry prismalGeometry = new PrismalGeometry(
                    rootWidth, rootHeight, geometry.centerX, geometry.centerY,
                    geometry.width, geometry.height, geometry.cornerRadius);
            prismalRenderer.drawGlass(
                    prismalGeometry, params, launcherHighlightProfile, state.interaction);
        }
        presentFull(prismalRenderer.outputTexture(), output);
    }

    private void renderDragOutputs(PrismalParams params) {
        for (Map.Entry<LauncherGlassSinkView, OutputState> entry
                : new ArrayList<>(outputs.entrySet())) {
            NodeState node = nodes.get(entry.getKey());
            LauncherGlassGeometry.Snapshot geometry = node != null ? node.geometry : null;
            if (geometry == null) continue;
            makePbufferCurrent();
            prismalRenderer.beginGlassFrame();
            PrismalGeometry prismalGeometry = new PrismalGeometry(
                    rootWidth, rootHeight, geometry.centerX, geometry.centerY,
                    geometry.width, geometry.height, geometry.cornerRadius);
            prismalRenderer.drawGlass(
                    prismalGeometry, params, launcherHighlightProfile, node.interaction);
            present(prismalRenderer.outputTexture(), geometry, entry.getValue());
        }
    }

    private void renderNormalizationRoot() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, rawFramebuffer);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glViewport(0, 0, rootWidth, rootHeight);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(normalizeProgram);
        bindQuad(normalizeProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
        GLES20.glUniform1i(requireUniform(normalizeProgram, "uTexture"), 0);
        GLES20.glUniformMatrix4fv(requireUniform(normalizeProgram, "uTexMatrix"),
                1, false, textureMatrix, 0);
        GLES20.glUniform1i(requireUniform(normalizeProgram, "uConfigRot"), configRotation);
        GLES20.glUniform4f(requireUniform(normalizeProgram, "uValidDockRect"), 0f, 0f, 1f, 1f);
        GLES20.glUniform4f(requireUniform(normalizeProgram, "uBackdropRect"), 0f, 0f, 1f, 1f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(normalizeProgram);
    }'''
session = replace_section(
        session,
        '    private void renderScene(boolean rebuildBackdrop) {',
        '    private void present(int sceneTexture, LauncherGlassGeometry.Snapshot geometry,',
        new_render,
        'root backdrop render path')

present_full = r'''    private void presentFull(int sceneTexture, OutputState output) {
        if (output == null || output.eglSurface == EGL14.EGL_NO_SURFACE
                || output.width <= 0 || output.height <= 0) return;
        makeCurrent(output.eglSurface);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, output.width, output.height);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(compositeProgram);
        bindQuad(compositeProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTexture);
        GLES20.glUniform1i(requireUniform(compositeProgram, "uTexture"), 0);
        GLES20.glUniform4f(requireUniform(compositeProgram, "uCropRect"), 0f, 0f, 1f, 1f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(compositeProgram);
        if (!EGL14.eglSwapBuffers(eglDisplay, output.eglSurface)) {
            throw new IllegalStateException("eglSwapBuffers(static) error=0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }
    }

'''
session = replace_once(session,
        '    private void present(int sceneTexture, LauncherGlassGeometry.Snapshot geometry,',
        present_full + '    private void present(int sceneTexture, LauncherGlassGeometry.Snapshot geometry,',
        'presentFull insertion')
session = replace_once(session,
'''        for (OutputState output : new ArrayList<>(outputs.values())) releaseOutput(output);
        outputs.clear();
''',
'''        releaseOutput(staticOutput);
        staticOutput = null;
        for (OutputState output : new ArrayList<>(outputs.values())) releaseOutput(output);
        outputs.clear();
''', 'release static output')
session_path.write_text(session)

# Update the old drag/static-specific source contracts to the new approved ownership model.
fd_path = TEST / 'FolderDragOverlayContractTest.java'
fd = fd_path.read_text()
old = r'''    @Test
    public void dragBridgeRegistersAuthoritativeStaticFolderSuppressionWhenSinkAppears() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherDragOverlayHook.java"));

        assertTrue(hook.contains("child instanceof LauncherGlassSinkView"));
        assertTrue(hook.contains("installStaticSinkDragSuppression"));
        assertTrue(hook.contains("onDragContainerBgAnimAlpha"));
        assertTrue(hook.contains("new Class<?>[]{Boolean.TYPE, Boolean.TYPE}"));
        assertTrue(hook.contains("setSuppressedByDrag(!normalState)"));
    }
'''
new = r'''    @Test
    public void dragBridgeRegistersAuthoritativeStaticFolderSuppressionOnLightweightNode() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherDragOverlayHook.java"));

        assertTrue(hook.contains("observeStaticNode"));
        assertTrue(hook.contains("installStaticNodeDragSuppression"));
        assertTrue(hook.contains("LauncherGlassStaticNode.find((View) target)"));
        assertTrue(hook.contains("onDragContainerBgAnimAlpha"));
        assertTrue(hook.contains("new Class<?>[]{Boolean.TYPE, Boolean.TYPE}"));
        assertTrue(hook.contains("setSuppressedByDrag(!normalState)"));
    }
'''
fd = replace_once(fd, old, new, 'FolderDragOverlayContractTest migration')
fd_path.write_text(fd)

fo_path = TEST / 'FolderOpenLifecycleContractTest.java'
fo = fo_path.read_text()
fo = fo.replace('String sink = read("LauncherGlassSinkView.java");',
                'String sink = read("LauncherGlassStaticNode.java");')
fo = fo.replace('folderOpenSuppressesOnlyItsOwnLauncherGlassSink',
                'folderOpenSuppressesOnlyItsOwnLauncherGlassStaticNode')
fo_path.write_text(fo)

fp_path = TEST / 'FolderPressInteractionContractTest.java'
fp = fp_path.read_text()
fp = fp.replace('String sink = read("LauncherGlassSinkView.java");',
                'String sink = read("LauncherGlassStaticNode.java");')
fp = fp.replace('sink detach must immediately clear stale press state',
                'static node detach must immediately clear stale press state')
fp = fp.replace('&& sink.indexOf("resetPressInteraction(false)") < sink.indexOf("super.onDetachedFromWindow()")',
                '&& sink.contains("onViewDetachedFromWindow")')
fp_path.write_text(fp)

# Self-check the architectural invariants before handing the tree to Gradle.
assert 'getGlobalVisibleRect' not in static_node
assert 'extends TextureView' not in static_node
assert 'new Surface(' not in static_node
assert 'LauncherGlassStaticNode.find(' in drag
assert 'findStaticSink(' not in drag
assert 'boolean atlasChanged = rebuildAtlasLayout(root);' not in session
assert 'renderNormalizationRoot' in session
assert 'presentFull(' in session
assert 'OutputState staticOutput' in session
assert 'LauncherGlassSinkView.attachToMaterial' not in folder
print('static compositor source migration applied')
