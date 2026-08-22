from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(rel):
    return (ROOT / rel).read_text()


def write(rel, text):
    (ROOT / rel).write_text(text)


def replace_once(rel, old, new):
    path = ROOT / rel
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{rel}: expected one match, got {count}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1))


def replace_regex(rel, pattern, replacement, flags=0):
    path = ROOT / rel
    text = path.read_text()
    new, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"{rel}: regex match count={count}: {pattern[:100]!r}")
    path.write_text(new)


# ---------------------------------------------------------------------------
# Workspace scene controller
# ---------------------------------------------------------------------------
write("src/main/java/com/hellovoid/liquiddock/LauncherGlassSceneController.java", r'''package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Sole owner of Workspace glass visibility, bootstrap freshness and scene generation. */
final class LauncherGlassSceneController {
    private static final String TAG = "[DC][GlassScene]";
    private static final WeakHashMap<View, LauncherGlassSceneController> BY_ROOT = new WeakHashMap<>();

    enum State { DETACHED, BOOTSTRAPPING, HOME_WAITING_FRESH_FRAME, HOME_VISIBLE, COVERED }

    /** Pure state machine kept Android-free for deterministic lifecycle tests. */
    static final class StateMachine {
        private State state = State.DETACHED;
        private long generation = 1L;
        private boolean covered;

        void onRootReady() {
            if (state == State.DETACHED) state = State.BOOTSTRAPPING;
        }

        void onBootstrapReconciled() {
            if (!covered && state == State.BOOTSTRAPPING) state = State.HOME_WAITING_FRESH_FRAME;
        }

        void setCovered(boolean nextCovered) {
            if (covered == nextCovered) return;
            covered = nextCovered;
            if (covered) {
                state = State.COVERED;
            } else {
                generation++;
                state = State.HOME_WAITING_FRESH_FRAME;
            }
        }

        void onGenerationInvalidated() {
            generation++;
            if (!covered && state != State.DETACHED) state = State.HOME_WAITING_FRESH_FRAME;
        }

        void onFreshFrameReady(long frameGeneration) {
            if (frameGeneration != generation || covered || state == State.DETACHED) return;
            state = State.HOME_VISIBLE;
        }

        void detach() {
            state = State.DETACHED;
            covered = false;
        }

        long generation() { return generation; }
        boolean isLayerVisible() { return state == State.HOME_VISIBLE; }
        State state() { return state; }
    }

    private final WeakReference<View> rootRef;
    private final LauncherGlassSession session;
    private final StateMachine state = new StateMachine();
    private volatile LiquidDockConfig.Glass glassConfig;
    private LauncherGlassStaticLayer layer;
    private boolean bootstrapPosted;

    private LauncherGlassSceneController(View root, LauncherGlassSession session,
                                         LiquidDockConfig.Glass glassConfig) {
        rootRef = new WeakReference<>(root);
        this.session = session;
        this.glassConfig = glassConfig;
    }

    static synchronized LauncherGlassSceneController acquire(
            View root, LauncherGlassSession session, LiquidDockConfig.Glass glassConfig) {
        if (root == null || session == null) return null;
        LauncherGlassSceneController current = BY_ROOT.get(root);
        if (current != null && current.session == session) {
            current.glassConfig = glassConfig;
            return current;
        }
        if (current != null) current.dispose();
        LauncherGlassSceneController created =
                new LauncherGlassSceneController(root, session, glassConfig);
        BY_ROOT.put(root, created);
        return created;
    }

    static synchronized LauncherGlassSceneController find(View anyView) {
        View root = LauncherGlassSessionRegistry.resolveStableRoot(anyView);
        return root != null ? BY_ROOT.get(root) : null;
    }

    static synchronized LauncherGlassSceneController findRoot(View root) {
        return root != null ? BY_ROOT.get(root) : null;
    }

    static void setWorkspaceCovered(View anyView, boolean covered) {
        LauncherGlassSceneController controller = find(anyView);
        if (controller != null) controller.setCovered(covered);
    }

    static void onFreshFrameRendered(View root, long generation) {
        LauncherGlassSceneController controller = findRoot(root);
        if (controller != null) controller.onFreshFrameReady(generation);
    }

    static long invalidateForProducerChange(View root) {
        LauncherGlassSceneController controller = findRoot(root);
        if (controller == null) return -1L;
        controller.state.onGenerationInvalidated();
        controller.applyLayerVisibility();
        long generation = controller.state.generation();
        if (root != null) root.postOnAnimation(() -> controller.requestFreshBackdrop(generation));
        return generation;
    }

    static void requestFreshForRoot(View root) {
        LauncherGlassSceneController controller = findRoot(root);
        if (controller != null) controller.requestFreshBackdrop(controller.state.generation());
    }

    void onRootReady() {
        View root = rootRef.get();
        if (root == null || !root.isAttachedToWindow()) return;
        state.onRootReady();
        if (layer == null) layer = LauncherGlassStaticLayer.acquire(root, session);
        applyLayerVisibility();
        if (bootstrapPosted) return;
        bootstrapPosted = true;
        root.postOnAnimation(() -> {
            bootstrapPosted = false;
            View liveRoot = rootRef.get();
            if (liveRoot == null || !liveRoot.isAttachedToWindow()) return;
            reconcileExistingWorkspace();
            state.onBootstrapReconciled();
            applyLayerVisibility();
            requestFreshBackdrop(state.generation());
        });
    }

    private void requestFreshBackdrop(long generation) {
        if (state.state() == State.COVERED || generation != state.generation()) return;
        session.requestFreshBackdrop(generation);
    }

    private void onFreshFrameReady(long generation) {
        state.onFreshFrameReady(generation);
        applyLayerVisibility();
    }

    private void setCovered(boolean covered) {
        boolean wasCovered = state.state() == State.COVERED;
        state.setCovered(covered);
        applyLayerVisibility();
        if (covered) {
            session.suspendWorkspaceProducer();
        } else if (wasCovered) {
            requestFreshBackdrop(state.generation());
        }
    }

    private void applyLayerVisibility() {
        LauncherGlassStaticLayer current = layer;
        if (current != null) current.setSceneVisible(state.isLayerVisible());
    }

    /** Startup barrier: scan objects that existed before constructor/attach hooks became useful. */
    private void reconcileExistingWorkspace() {
        View root = rootRef.get();
        if (root == null) return;
        View workspace = findWorkspace(root);
        scan(workspace != null ? workspace : root);
        MainHook.log(TAG + " bootstrap reconciled generation=" + state.generation());
    }

    private void scan(View view) {
        if (view == null || view == layer) return;
        LiquidDockConfig.Glass config = glassConfig;
        if (config != null) {
            MiuixFolderGlassHook.reconcileExistingView(view, config);
            MiuixLauncherStaticGlassHook.reconcileExistingHost(view, config);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) scan(group.getChildAt(i));
        }
    }

    private static View findWorkspace(View view) {
        if (view == null) return null;
        String simple = view.getClass().getSimpleName();
        if ("Workspace".equals(simple)
                || "com.miui.home.launcher.Workspace".equals(view.getClass().getName())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findWorkspace(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    void dispose() {
        View root = rootRef.get();
        synchronized (LauncherGlassSceneController.class) {
            if (root != null && BY_ROOT.get(root) == this) BY_ROOT.remove(root);
        }
        state.detach();
        LauncherGlassStaticLayer current = layer;
        layer = null;
        if (current != null) current.dispose();
    }
}
''')

# ---------------------------------------------------------------------------
# Passive static layer + root registry ownership
# ---------------------------------------------------------------------------
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticLayer.java",
    "        setOpaque(false);\n        setClickable(false);",
    "        setOpaque(false);\n        setVisibility(View.INVISIBLE);\n        setClickable(false);",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticLayer.java",
    "    void dispose() {\n",
    "    void setSceneVisible(boolean visible) {\n"
    "        if (disposed) return;\n"
    "        int target = visible ? View.VISIBLE : View.INVISIBLE;\n"
    "        if (getVisibility() != target) setVisibility(target);\n"
    "    }\n\n"
    "    void dispose() {\n",
)

write("src/main/java/com/hellovoid/liquiddock/LauncherGlassSessionRegistry.java", r'''package com.hellovoid.liquiddock;

import android.view.View;

import java.util.WeakHashMap;

/** One shared GPU glass session and one scene controller per stable Launcher ViewRoot. */
final class LauncherGlassSessionRegistry {
    private static final WeakHashMap<View, LauncherGlassSession> SESSIONS = new WeakHashMap<>();

    private LauncherGlassSessionRegistry() {}

    static synchronized LauncherGlassSession acquire(
            View materialHost, LiquidDockConfig.Glass glassConfig) {
        View root = resolveStableRoot(materialHost);
        if (root == null) return null;
        LauncherGlassSession current = SESSIONS.get(root);
        if (current != null && !current.isShutdown()) {
            current.setGlassConfig(glassConfig);
            LauncherGlassSceneController controller =
                    LauncherGlassSceneController.acquire(root, current, glassConfig);
            if (controller != null) controller.onRootReady();
            return current;
        }
        LauncherGlassSession created = new LauncherGlassSession(root, glassConfig);
        SESSIONS.put(root, created);
        LauncherGlassSceneController controller =
                LauncherGlassSceneController.acquire(root, created, glassConfig);
        if (controller != null) controller.onRootReady();
        return created;
    }

    static View resolveStableRoot(View materialHost) {
        if (materialHost == null || !materialHost.isAttachedToWindow()
                || materialHost.getWidth() <= 0 || materialHost.getHeight() <= 0) return null;
        View root = materialHost.getRootView();
        if (root == null || root == materialHost || !root.isAttachedToWindow()
                || root.getWidth() <= 0 || root.getHeight() <= 0 || root.getWindowToken() == null) {
            return null;
        }
        return root;
    }

    static synchronized void forget(View root, LauncherGlassSession session) {
        if (root == null || session == null) return;
        if (SESSIONS.get(root) == session) {
            SESSIONS.remove(root);
            LauncherGlassSceneController controller = LauncherGlassSceneController.findRoot(root);
            if (controller != null) controller.dispose();
        }
    }
}
''')

# ---------------------------------------------------------------------------
# LauncherGlassSession: generation + explicit freshness API + geometry gate
# ---------------------------------------------------------------------------
SESSION = "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java"
replace_once(
    SESSION,
    "    private volatile boolean hasConsumedFrame;\n",
    "    private volatile boolean hasConsumedFrame;\n"
    "    private volatile long sceneGeneration = 1L;\n"
    "    private volatile long consumedGeneration = -1L;\n",
)
replace_once(
    SESSION,
    "        View root = rootRef.get();\n"
    "        if (root != null) {\n"
    "            mainHandler.post(() -> {\n"
    "                if (!shuttingDown && ownsRoot(root)) LauncherGlassStaticLayer.acquire(root, this);\n"
    "            });\n"
    "        }\n"
    "        syncSceneOnUiThread();\n"
    "        requestLifecycleRefresh();\n",
    "        syncSceneOnUiThread();\n"
    "        requestSceneRedraw();\n",
)
replace_once(
    SESSION,
    "    void requestLifecycleRefresh() {\n"
    "        if (shuttingDown) return;\n"
    "        requestFrame(false);\n"
    "    }\n",
    "    void requestLifecycleRefresh() {\n"
    "        requestSceneRedraw();\n"
    "    }\n\n"
    "    void invalidateGeneration(long generation) {\n"
    "        if (shuttingDown || generation < sceneGeneration) return;\n"
    "        sceneGeneration = generation;\n"
    "        frameAvailable.set(false);\n"
    "        hasConsumedFrame = false;\n"
    "        consumedGeneration = -1L;\n"
    "        backdropPrepared = false;\n"
    "    }\n\n"
    "    void requestFreshBackdrop(long generation) {\n"
    "        if (shuttingDown || generation < sceneGeneration) return;\n"
    "        invalidateGeneration(generation);\n"
    "        requestFrame(true);\n"
    "    }\n\n"
    "    void requestSceneRedraw() {\n"
    "        if (shuttingDown) return;\n"
    "        requestFrame(false);\n"
    "    }\n\n"
    "    void suspendWorkspaceProducer() {\n"
    "        if (shuttingDown) return;\n"
    "        Miuix307PassBlurBridge.pauseUpdates(binding);\n"
    "    }\n",
)
replace_once(
    SESSION,
    "                staticOutput = next;\n"
    "                requestFrame(false);\n",
    "                staticOutput = next;\n"
    "                requestFrame(false);\n"
    "                mainHandler.post(() ->\n"
    "                        LauncherGlassSceneController.requestFreshForRoot(rootRef.get()));\n",
)
replace_once(
    SESSION,
    "        boolean rootGeometryChanged = width != rootWidth || height != rootHeight;\n",
    "        boolean rootGeometryChanged = width != rootWidth || height != rootHeight;\n",
)
replace_once(
    SESSION,
    "                LauncherGlassGeometry.Snapshot selected = state.geometryStability.select(observed);\n",
    "                LauncherGlassGeometry.Snapshot selected = rootGeometryChanged\n"
    "                        ? observed : state.geometryStability.select(observed);\n",
)
replace_once(
    SESSION,
    "                LauncherGlassGeometry.Snapshot selected = state.geometryStability.select(observed);\n",
    "                LauncherGlassGeometry.Snapshot selected = rootGeometryChanged\n"
    "                        ? observed : state.geometryStability.select(observed);\n",
)
replace_once(
    SESSION,
    "        ProducerGeometry geometry = readSurfaceGeometry(root);\n"
    "        if (geometry == null) return false;\n",
    "        ProducerGeometry geometry = readSurfaceGeometry(root);\n"
    "        if (geometry == null) return false;\n"
    "        if (!LauncherGlassProducerGeometryGate.matchesRoot(\n"
    "                rootWidth, rootHeight, geometry.surfaceWidth, geometry.surfaceHeight,\n"
    "                geometry.insetLeft, geometry.insetTop, geometry.insetRight, geometry.insetBottom)) {\n"
    "            frameAvailable.set(false);\n"
    "            hasConsumedFrame = false;\n"
    "            consumedGeneration = -1L;\n"
    "            MainHook.log(TAG + \" producer geometry not coherent with root root=\"\n"
    "                    + rootWidth + \"x\" + rootHeight + \" surface=\"\n"
    "                    + geometry.surfaceWidth + \"x\" + geometry.surfaceHeight);\n"
    "            return false;\n"
    "        }\n",
)
replace_once(
    SESSION,
    "        if (changed) {\n"
    "            boundBufferWidth = geometry.bufferWidth;\n",
    "        if (changed) {\n"
    "            frameAvailable.set(false);\n"
    "            hasConsumedFrame = false;\n"
    "            consumedGeneration = -1L;\n"
    "            long nextGeneration = LauncherGlassSceneController.invalidateForProducerChange(root);\n"
    "            if (nextGeneration > 0L) sceneGeneration = nextGeneration;\n"
    "            boundBufferWidth = geometry.bufferWidth;\n",
)
replace_once(
    SESSION,
    "            input.updateTexImage();\n"
    "            input.getTransformMatrix(textureMatrix);\n"
    "            hasConsumedFrame = true;\n"
    "            sourceChanged = true;\n",
    "            input.updateTexImage();\n"
    "            input.getTransformMatrix(textureMatrix);\n"
    "            hasConsumedFrame = true;\n"
    "            consumedGeneration = sceneGeneration;\n"
    "            sourceChanged = true;\n",
)
replace_once(
    SESSION,
    "        renderScene(staticSnapshot, nodeSnapshot);\n"
    "        framePolicy.onRendered();\n",
    "        renderScene(staticSnapshot, nodeSnapshot);\n"
    "        long renderedGeneration = consumedGeneration;\n"
    "        if (sourceChanged && staticOutput != null && renderedGeneration == sceneGeneration) {\n"
    "            View root = rootRef.get();\n"
    "            mainHandler.post(() -> LauncherGlassSceneController.onFreshFrameRendered(\n"
    "                    root, renderedGeneration));\n"
    "        }\n"
    "        framePolicy.onRendered();\n",
)

# ---------------------------------------------------------------------------
# Static node classification, effective visibility and component size/radius style
# ---------------------------------------------------------------------------
NODE = "src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java"
replace_once(
    NODE,
    "    private final LauncherGlassDragState.Kind kind;\n",
    "    private final LauncherGlassDragState.Kind kind;\n"
    "    private final LauncherGlassNodeKind nodeKind;\n",
)
replace_once(
    NODE,
    "            LauncherGlassDragState.Kind kind,\n"
    "            LauncherGlassSession session,\n",
    "            LauncherGlassDragState.Kind kind,\n"
    "            LauncherGlassNodeKind nodeKind,\n"
    "            LauncherGlassSession session,\n",
)
replace_once(
    NODE,
    "        this.kind = kind != null ? kind : LauncherGlassDragState.Kind.FOLDER;\n"
    "        this.session = session;\n",
    "        this.kind = kind != null ? kind : LauncherGlassDragState.Kind.FOLDER;\n"
    "        this.nodeKind = nodeKind != null ? nodeKind : LauncherGlassNodeKind.LARGE_FOLDER;\n"
    "        this.session = session;\n",
)
replace_once(
    NODE,
    "        LauncherGlassDragState.Kind resolvedKind = kind != null\n"
    "                ? kind : LauncherGlassDragState.Kind.FOLDER;\n",
    "        LauncherGlassDragState.Kind resolvedKind = kind != null\n"
    "                ? kind : LauncherGlassDragState.Kind.FOLDER;\n"
    "        LauncherGlassNodeKind resolvedNodeKind = resolvedKind == LauncherGlassDragState.Kind.ICON\n"
    "                ? LauncherGlassNodeKind.ICON\n"
    "                : resolvedKind == LauncherGlassDragState.Kind.WIDGET\n"
    "                ? LauncherGlassNodeKind.WIDGET : LauncherGlassNodeKind.LARGE_FOLDER;\n",
)
replace_once(
    NODE,
    "        if (existing != null && !existing.disposed && existing.kind == resolvedKind) {\n",
    "        if (existing != null && !existing.disposed && existing.kind == resolvedKind\n"
    "                && existing.nodeKind == resolvedNodeKind) {\n",
)
replace_once(
    NODE,
    "        LauncherGlassStaticNode node = new LauncherGlassStaticNode(\n"
    "                materialHost, resolvedKind, shared, cornerRadiusPx, glassConfig);\n",
    "        LauncherGlassStaticNode node = new LauncherGlassStaticNode(\n"
    "                materialHost, resolvedKind, resolvedNodeKind, shared, cornerRadiusPx, glassConfig);\n",
)
replace_once(
    NODE,
    "    static LauncherGlassStaticNode find(View materialHost) {\n",
    "    static LauncherGlassStaticNode attachFolderMaterial(\n"
    "            View materialHost, boolean smallFolder, float cornerRadiusPx,\n"
    "            LiquidDockConfig.Glass glassConfig) {\n"
    "        if (materialHost == null) return null;\n"
    "        LauncherGlassNodeKind resolvedNodeKind = smallFolder\n"
    "                ? LauncherGlassNodeKind.SMALL_FOLDER : LauncherGlassNodeKind.LARGE_FOLDER;\n"
    "        WeakReference<LauncherGlassStaticNode> reference = BY_MATERIAL.get(materialHost);\n"
    "        LauncherGlassStaticNode existing = reference != null ? reference.get() : null;\n"
    "        if (existing != null && !existing.disposed && existing.nodeKind == resolvedNodeKind) {\n"
    "            existing.setNativeCornerRadiusPx(cornerRadiusPx);\n"
    "            LauncherGlassSession live = existing.ensureLiveSession();\n"
    "            if (live != null) live.registerStaticNode(existing);\n"
    "            return existing;\n"
    "        }\n"
    "        if (existing != null && !existing.disposed) existing.dispose();\n"
    "        LauncherGlassSession shared = LauncherGlassSessionRegistry.acquire(materialHost, glassConfig);\n"
    "        if (shared == null) return null;\n"
    "        LauncherGlassStaticNode node = new LauncherGlassStaticNode(materialHost,\n"
    "                LauncherGlassDragState.Kind.FOLDER, resolvedNodeKind, shared,\n"
    "                cornerRadiusPx, glassConfig);\n"
    "        BY_MATERIAL.put(materialHost, new WeakReference<>(node));\n"
    "        shared.registerStaticNode(node);\n"
    "        return node;\n"
    "    }\n\n"
    "    static LauncherGlassStaticNode find(View materialHost) {\n",
)
replace_once(
    NODE,
    "    LauncherGlassDragState.Kind kind() { return kind; }\n",
    "    LauncherGlassDragState.Kind kind() { return kind; }\n"
    "    LauncherGlassNodeKind nodeKind() { return nodeKind; }\n\n"
    "    private GlassComponentStyle componentStyle() {\n"
    "        if (glassConfig == null) return new GlassComponentStyle(true, 0f, 0f);\n"
    "        switch (nodeKind) {\n"
    "            case ICON: return glassConfig.iconStyle;\n"
    "            case WIDGET: return glassConfig.widgetStyle;\n"
    "            case SMALL_FOLDER: return glassConfig.smallFolderStyle;\n"
    "            case LARGE_FOLDER:\n"
    "            default: return glassConfig.largeFolderStyle;\n"
    "        }\n"
    "    }\n",
)
replace_once(
    NODE,
    "        if (disposed || material == null || root == null\n"
    "                || suppressedByFolderOpen || suppressedByDrag\n"
    "                || !material.isAttachedToWindow() || !material.isShown()\n"
    "                || material.getAlpha() <= 0f) return null;\n",
    "        GlassComponentStyle style = componentStyle();\n"
    "        if (disposed || material == null || root == null || style == null || !style.enabled\n"
    "                || suppressedByFolderOpen || suppressedByDrag\n"
    "                || !LauncherGlassVisibility.isVisible(material, root)) return null;\n",
)
replace_once(
    NODE,
    "        float localWidth = Math.max(1f, localRight - localLeft);\n"
    "        float localHeight = Math.max(1f, localBottom - localTop);\n",
    "        float density = material.getResources().getDisplayMetrics().density;\n"
    "        float[] styledBounds = LauncherGlassBoundsPolicy.apply(\n"
    "                localLeft, localTop, localRight, localBottom, style.sizeOffsetDp * density);\n"
    "        localLeft = styledBounds[0];\n"
    "        localTop = styledBounds[1];\n"
    "        localRight = styledBounds[2];\n"
    "        localBottom = styledBounds[3];\n"
    "        float localWidth = Math.max(1f, localRight - localLeft);\n"
    "        float localHeight = Math.max(1f, localBottom - localTop);\n",
)
replace_once(
    NODE,
    "        return LauncherGlassGeometry.resolve(\n"
    "                root.getWidth(), root.getHeight(), left, top, right, bottom,\n"
    "                nativeCornerRadiusPx * radiusScale);\n",
    "        float requestedRadius = style.cornerRadiusDp > 0f\n"
    "                ? style.cornerRadiusDp * density : nativeCornerRadiusPx;\n"
    "        return LauncherGlassGeometry.resolve(\n"
    "                root.getWidth(), root.getHeight(), left, top, right, bottom,\n"
    "                LauncherGlassBoundsPolicy.capRadius(\n"
    "                        requestedRadius * radiusScale, right - left, bottom - top));\n",
)

# Dock item visibility is rooted at the Floating Dock window, while geometry is relative to body.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/DockGlassItemNode.java",
    "                || !LauncherGlassVisibility.isVisible(view, dockRoot)\n",
    "                || !LauncherGlassVisibility.isVisible(view, dockRoot.getRootView())\n",
)

# ---------------------------------------------------------------------------
# Existing host hooks: bootstrap reconciliation + safe widget/folder material ownership
# ---------------------------------------------------------------------------
STATIC_HOOK = "src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java"
replace_once(
    STATIC_HOOK,
    "    private static void observeHost(\n",
    "    static void reconcileExistingHost(View host, LiquidDockConfig.Glass glassConfig) {\n"
    "        if (host == null || glassConfig == null) return;\n"
    "        String name = host.getClass().getName();\n"
    "        if (glassConfig.iconStyle.enabled && (name.endsWith(\".ShortcutIcon\")\n"
    "                || \"ShortcutIcon\".equals(host.getClass().getSimpleName()))) {\n"
    "            observeHost(host, LauncherGlassDragState.Kind.ICON, glassConfig);\n"
    "        } else if (glassConfig.widgetStyle.enabled\n"
    "                && (name.endsWith(\".LauncherAppWidgetHostView\")\n"
    "                || name.endsWith(\".MaMlHostView\"))) {\n"
    "            LauncherGlassVendorMaterialSuppressor.claimWidget(host);\n"
    "            observeHost(host, LauncherGlassDragState.Kind.WIDGET, glassConfig);\n"
    "        }\n"
    "    }\n\n"
    "    private static void observeHost(\n",
)
replace_once(
    STATIC_HOOK,
    "        if (node != null) removeBootstrapObserver(host);\n",
    "        if (node != null) {\n"
    "            if (kind == LauncherGlassDragState.Kind.WIDGET)\n"
    "                LauncherGlassVendorMaterialSuppressor.claimWidget(host);\n"
    "            removeBootstrapObserver(host);\n"
    "        }\n",
)
replace_once(
    STATIC_HOOK,
    "            return Math.max(0f, min * 0.22f);\n",
    "            android.graphics.drawable.Drawable drawable = null;\n"
    "            if (host instanceof android.widget.TextView) {\n"
    "                android.graphics.drawable.Drawable[] drawables =\n"
    "                        ((android.widget.TextView) host).getCompoundDrawables();\n"
    "                if (drawables.length > 1) drawable = drawables[1];\n"
    "            }\n"
    "            return LauncherGlassIconShapeResolver.resolveAutoRadius(\n"
    "                    drawable, min, min, min * 0.22f);\n",
)

FOLDER = "src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java"
replace_once(FOLDER, "import java.lang.reflect.Field;\n", "import java.lang.reflect.Constructor;\nimport java.lang.reflect.Field;\n")
replace_once(
    FOLDER,
    "            installFolderOpenCloseHooks(classLoader);\n",
    "            installFolderOpenCloseHooks(classLoader);\n"
    "            observeFolderVariantConstructors(classLoader, glassConfig);\n",
)
replace_once(
    FOLDER,
    "    private static void installFolderOpenCloseHooks(ClassLoader classLoader) throws Exception {\n",
    "    private static void observeFolderVariantConstructors(\n"
    "            ClassLoader classLoader, LiquidDockConfig.Glass glassConfig) {\n"
    "        String[] variants = {\"com.miui.home.launcher.FolderIcon1x1\",\n"
    "                \"com.miui.home.launcher.FolderIcon2x2\"};\n"
    "        for (String variant : variants) {\n"
    "            try {\n"
    "                Class<?> type = Class.forName(variant, false, classLoader);\n"
    "                for (Constructor<?> constructor : type.getDeclaredConstructors()) {\n"
    "                    HookUtil.hook(constructor, chain -> {\n"
    "                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));\n"
    "                        Object owner = chain.getThisObject();\n"
    "                        if (owner instanceof ViewGroup) attachFromFolderIcon((ViewGroup) owner, glassConfig);\n"
    "                        return result;\n"
    "                    });\n"
    "                }\n"
    "            } catch (Throwable ignored) {}\n"
    "        }\n"
    "    }\n\n"
    "    static void reconcileExistingView(View view, LiquidDockConfig.Glass glassConfig) {\n"
    "        if (!(view instanceof ViewGroup) || glassConfig == null) return;\n"
    "        String name = view.getClass().getName();\n"
    "        if (name.endsWith(\".FolderIcon\") || name.contains(\"FolderIcon1x1\")\n"
    "                || name.contains(\"FolderIcon2x2\")) {\n"
    "            attachFromFolderIcon((ViewGroup) view, glassConfig);\n"
    "        }\n"
    "    }\n\n"
    "    private static void installFolderOpenCloseHooks(ClassLoader classLoader) throws Exception {\n",
)
# Replace the three direct mIconImageView lookups in attachment/recovery/attach-listener paths.
replace_once(
    FOLDER,
    "            Object value = HookUtil.getField(icon, \"mIconImageView\");\n"
    "            if (value instanceof View) {\n"
    "                LauncherGlassStaticNode sink = attachMaterial((View) value, glassConfig);\n",
    "            View value = resolveFolderMaterial(icon);\n"
    "            if (value != null) {\n"
    "                LauncherGlassStaticNode sink = attachMaterial(value, glassConfig);\n",
)
replace_once(
    FOLDER,
    "                Object value = HookUtil.getField(current, \"mIconImageView\");\n"
    "                if (value instanceof View) {\n"
    "                    sink = attachMaterial((View) value, glassConfig);\n",
    "                View value = resolveFolderMaterial(current);\n"
    "                if (value != null) {\n"
    "                    sink = attachMaterial(value, glassConfig);\n",
)
replace_once(
    FOLDER,
    "                    Object value = HookUtil.getField(folder, \"mIconImageView\");\n"
    "                    if (value instanceof View) {\n"
    "                        LauncherGlassStaticNode sink = claimedSink((View) value);\n",
    "                    View value = resolveFolderMaterial(folder);\n"
    "                    if (value != null) {\n"
    "                        LauncherGlassStaticNode sink = claimedSink(value);\n",
)
replace_once(
    FOLDER,
    "    private static LauncherGlassStaticNode attachMaterial(\n",
    "    private static View resolveFolderMaterial(ViewGroup folder) {\n"
    "        if (folder == null) return null;\n"
    "        boolean small = folder.getClass().getName().contains(\"FolderIcon1x1\");\n"
    "        String[] fields = small\n"
    "                ? new String[]{\"mImageView\", \"mIconImageView\"}\n"
    "                : new String[]{\"mIconImageView\", \"mImageView\"};\n"
    "        for (String field : fields) {\n"
    "            try {\n"
    "                Object value = HookUtil.getField(folder, field);\n"
    "                if (value instanceof View) return (View) value;\n"
    "            } catch (Throwable ignored) {}\n"
    "        }\n"
    "        return null;\n"
    "    }\n\n"
    "    private static boolean isSmallFolderMaterial(View material) {\n"
    "        View cursor = material;\n"
    "        while (cursor != null) {\n"
    "            if (cursor.getClass().getName().contains(\"FolderIcon1x1\")) return true;\n"
    "            android.view.ViewParent parent = cursor.getParent();\n"
    "            cursor = parent instanceof View ? (View) parent : null;\n"
    "        }\n"
    "        return false;\n"
    "    }\n\n"
    "    private static LauncherGlassStaticNode attachMaterial(\n",
)
replace_once(
    FOLDER,
    "        float radius = LauncherGlassCornerRadiusPolicy.resolve(\n"
    "                glassConfig != null ? glassConfig.folderCornerRadiusDp : 0f,\n"
    "                density, nativeRadius, fallbackRadius);\n"
    "        LauncherGlassStaticNode sink = LauncherGlassStaticNode.attachToMaterial(\n"
    "                material, LauncherGlassDragState.Kind.FOLDER, radius, glassConfig);\n",
    "        boolean smallFolder = isSmallFolderMaterial(material);\n"
    "        GlassComponentStyle style = glassConfig != null\n"
    "                ? (smallFolder ? glassConfig.smallFolderStyle : glassConfig.largeFolderStyle)\n"
    "                : new GlassComponentStyle(true, 0f, 0f);\n"
    "        float radius = LauncherGlassCornerRadiusPolicy.resolve(\n"
    "                style.cornerRadiusDp, density, nativeRadius, fallbackRadius);\n"
    "        LauncherGlassStaticNode sink = LauncherGlassStaticNode.attachFolderMaterial(\n"
    "                material, smallFolder, radius, glassConfig);\n",
)
replace_once(
    FOLDER,
    "        MiBlurBridge.clearContentBlur(material);\n",
    "        LauncherGlassVendorMaterialSuppressor.claimFolderMaterial(material);\n"
    "        MiBlurBridge.clearContentBlur(material);\n",
)
# Global scene coverage during opened-folder overlay.
replace_once(
    FOLDER,
    "        if (owner == null) return;\n"
    "        if (!suppressed) {\n",
    "        if (owner == null) return;\n"
    "        LauncherGlassSceneController.setWorkspaceCovered(owner, suppressed);\n"
    "        if (!suppressed) {\n",
)
replace_once(
    FOLDER,
    "    private static void restoreOpenedFolderOwner() {\n"
    "        LauncherGlassStaticNode sink = openedFolderSink.get();\n"
    "        openedFolderOwner = new WeakReference<>(null);\n",
    "    private static void restoreOpenedFolderOwner() {\n"
    "        LauncherGlassStaticNode sink = openedFolderSink.get();\n"
    "        ViewGroup owner = openedFolderOwner.get();\n"
    "        if (owner != null) LauncherGlassSceneController.setWorkspaceCovered(owner, false);\n"
    "        openedFolderOwner = new WeakReference<>(null);\n",
)

# ---------------------------------------------------------------------------
# Four component styles + migration + both settings UIs
# ---------------------------------------------------------------------------
SCHEMA = "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"
replace_once(
    SCHEMA,
    "        public static final ConfigKey<Integer> FOLDER_CORNER_RADIUS = integer(\n"
    "                \"liquid_folder_corner_radius\", 0, 0, 0, 0, 96, ConfigKey.ExportMode.ALWAYS);\n",
    "        public static final ConfigKey<Integer> FOLDER_CORNER_RADIUS = integer(\n"
    "                \"liquid_folder_corner_radius\", 0, 0, 0, 0, 96, ConfigKey.ExportMode.IF_PRESENT);\n"
    "        public static final ConfigKey<Integer> ICON_SIZE_OFFSET = dp(\n"
    "                \"liquid_icon_size_offset\", 0, 0, 0, -40, 40, ConfigKey.ExportMode.ALWAYS);\n"
    "        public static final ConfigKey<Integer> ICON_CORNER_RADIUS = dp(\n"
    "                \"liquid_icon_corner_radius\", 0, 0, 0, 0, 128, ConfigKey.ExportMode.ALWAYS);\n"
    "        public static final ConfigKey<Integer> WIDGET_SIZE_OFFSET = dp(\n"
    "                \"liquid_widget_size_offset\", 0, 0, 0, -40, 40, ConfigKey.ExportMode.ALWAYS);\n"
    "        public static final ConfigKey<Integer> WIDGET_CORNER_RADIUS = dp(\n"
    "                \"liquid_widget_corner_radius\", 0, 0, 0, 0, 128, ConfigKey.ExportMode.ALWAYS);\n"
    "        public static final ConfigKey<Boolean> SMALL_FOLDER_GLASS = bool(\n"
    "                \"liquid_small_folder_glass\", true, true, true, ConfigKey.ExportMode.ALWAYS);\n"
    "        public static final ConfigKey<Integer> SMALL_FOLDER_SIZE_OFFSET = dp(\n"
    "                \"liquid_small_folder_size_offset\", 0, 0, 0, -40, 40, ConfigKey.ExportMode.ALWAYS);\n"
    "        public static final ConfigKey<Integer> SMALL_FOLDER_CORNER_RADIUS = dp(\n"
    "                \"liquid_small_folder_corner_radius\", 0, 0, 0, 0, 128, ConfigKey.ExportMode.ALWAYS);\n"
    "        public static final ConfigKey<Boolean> LARGE_FOLDER_GLASS = bool(\n"
    "                \"liquid_large_folder_glass\", true, true, true, ConfigKey.ExportMode.ALWAYS);\n"
    "        public static final ConfigKey<Integer> LARGE_FOLDER_SIZE_OFFSET = dp(\n"
    "                \"liquid_large_folder_size_offset\", 0, 0, 0, -40, 40, ConfigKey.ExportMode.ALWAYS);\n"
    "        public static final ConfigKey<Integer> LARGE_FOLDER_CORNER_RADIUS = dp(\n"
    "                \"liquid_large_folder_corner_radius\", 0, 0, 0, 0, 128, ConfigKey.ExportMode.ALWAYS);\n",
)
replace_once(
    SCHEMA,
    "        add(keys, Glass.ENABLED, Glass.FOLDER_GLASS, Glass.WIDGET_GLASS, Glass.ICON_GLASS,\n"
    "                Glass.FOLDER_CORNER_RADIUS,\n",
    "        add(keys, Glass.ENABLED, Glass.FOLDER_GLASS, Glass.WIDGET_GLASS, Glass.ICON_GLASS,\n"
    "                Glass.FOLDER_CORNER_RADIUS,\n"
    "                Glass.ICON_SIZE_OFFSET, Glass.ICON_CORNER_RADIUS,\n"
    "                Glass.WIDGET_SIZE_OFFSET, Glass.WIDGET_CORNER_RADIUS,\n"
    "                Glass.SMALL_FOLDER_GLASS, Glass.SMALL_FOLDER_SIZE_OFFSET,\n"
    "                Glass.SMALL_FOLDER_CORNER_RADIUS, Glass.LARGE_FOLDER_GLASS,\n"
    "                Glass.LARGE_FOLDER_SIZE_OFFSET, Glass.LARGE_FOLDER_CORNER_RADIUS,\n",
)

CONFIG = "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java"
replace_once(
    CONFIG,
    "        final boolean enabled, folderEnabled, widgetEnabled, iconEnabled;\n"
    "        final float folderCornerRadiusDp;\n",
    "        final boolean enabled, folderEnabled, widgetEnabled, iconEnabled;\n"
    "        final float folderCornerRadiusDp;\n"
    "        final GlassComponentStyle iconStyle;\n"
    "        final GlassComponentStyle widgetStyle;\n"
    "        final GlassComponentStyle smallFolderStyle;\n"
    "        final GlassComponentStyle largeFolderStyle;\n",
)
replace_once(
    CONFIG,
    "            folderEnabled = c.b(ConfigSchema.Glass.FOLDER_GLASS.name(),\n"
    "                    ConfigSchema.Glass.FOLDER_GLASS.runtimeFallback());\n"
    "            widgetEnabled = c.b(ConfigSchema.Glass.WIDGET_GLASS.name(),\n"
    "                    ConfigSchema.Glass.WIDGET_GLASS.runtimeFallback());\n"
    "            iconEnabled = c.b(ConfigSchema.Glass.ICON_GLASS.name(),\n"
    "                    ConfigSchema.Glass.ICON_GLASS.runtimeFallback());\n"
    "            folderCornerRadiusDp = c.f(ConfigSchema.Glass.FOLDER_CORNER_RADIUS.name(),\n"
    "                    ConfigSchema.Glass.FOLDER_CORNER_RADIUS.runtimeFallback());\n",
    "            boolean legacyFolderEnabled = c.b(\"liquid_folder_glass\",\n"
    "                    ConfigSchema.Glass.FOLDER_GLASS.runtimeFallback());\n"
    "            float legacyFolderRadius = c.f(\"liquid_folder_corner_radius\",\n"
    "                    ConfigSchema.Glass.FOLDER_CORNER_RADIUS.runtimeFallback());\n"
    "            boolean resolvedIconEnabled = c.b(ConfigSchema.Glass.ICON_GLASS.name(),\n"
    "                    ConfigSchema.Glass.ICON_GLASS.runtimeFallback());\n"
    "            boolean resolvedWidgetEnabled = c.b(ConfigSchema.Glass.WIDGET_GLASS.name(),\n"
    "                    ConfigSchema.Glass.WIDGET_GLASS.runtimeFallback());\n"
    "            boolean resolvedSmallEnabled = c.has(ConfigSchema.Glass.SMALL_FOLDER_GLASS.name())\n"
    "                    ? c.b(ConfigSchema.Glass.SMALL_FOLDER_GLASS.name(), true)\n"
    "                    : legacyFolderEnabled;\n"
    "            boolean resolvedLargeEnabled = c.has(ConfigSchema.Glass.LARGE_FOLDER_GLASS.name())\n"
    "                    ? c.b(ConfigSchema.Glass.LARGE_FOLDER_GLASS.name(), true)\n"
    "                    : legacyFolderEnabled;\n"
    "            float smallRadius = c.has(ConfigSchema.Glass.SMALL_FOLDER_CORNER_RADIUS.name())\n"
    "                    ? c.f(ConfigSchema.Glass.SMALL_FOLDER_CORNER_RADIUS.name(), 0f)\n"
    "                    : legacyFolderRadius;\n"
    "            float largeRadius = c.has(ConfigSchema.Glass.LARGE_FOLDER_CORNER_RADIUS.name())\n"
    "                    ? c.f(ConfigSchema.Glass.LARGE_FOLDER_CORNER_RADIUS.name(), 0f)\n"
    "                    : legacyFolderRadius;\n"
    "            iconStyle = new GlassComponentStyle(resolvedIconEnabled,\n"
    "                    c.f(ConfigSchema.Glass.ICON_SIZE_OFFSET.name(), 0f),\n"
    "                    c.f(ConfigSchema.Glass.ICON_CORNER_RADIUS.name(), 0f));\n"
    "            widgetStyle = new GlassComponentStyle(resolvedWidgetEnabled,\n"
    "                    c.f(ConfigSchema.Glass.WIDGET_SIZE_OFFSET.name(), 0f),\n"
    "                    c.f(ConfigSchema.Glass.WIDGET_CORNER_RADIUS.name(), 0f));\n"
    "            smallFolderStyle = new GlassComponentStyle(resolvedSmallEnabled,\n"
    "                    c.f(ConfigSchema.Glass.SMALL_FOLDER_SIZE_OFFSET.name(), 0f), smallRadius);\n"
    "            largeFolderStyle = new GlassComponentStyle(resolvedLargeEnabled,\n"
    "                    c.f(ConfigSchema.Glass.LARGE_FOLDER_SIZE_OFFSET.name(), 0f), largeRadius);\n"
    "            iconEnabled = iconStyle.enabled;\n"
    "            widgetEnabled = widgetStyle.enabled;\n"
    "            folderEnabled = smallFolderStyle.enabled || largeFolderStyle.enabled;\n"
    "            folderCornerRadiusDp = legacyFolderRadius;\n",
)

MIGRATION = "src/main/java/com/hellovoid/liquiddock/config/ConfigMigration.java"
replace_once(
    MIGRATION,
    "        resetUnsupportedGlassConfigGeneration(preferences);\n"
    "        migrateMergedHorizontal(preferences);\n",
    "        resetUnsupportedGlassConfigGeneration(preferences);\n"
    "        migrateGlassComponentStyles(preferences);\n"
    "        migrateMergedHorizontal(preferences);\n",
)
replace_once(
    MIGRATION,
    "    private static void migrateAxisDistances(SharedPreferences sp) {\n",
    "    private static void migrateGlassComponentStyles(SharedPreferences sp) {\n"
    "        boolean legacyEnabled = sp.getBoolean(\"liquid_folder_glass\", true);\n"
    "        int legacyRadius = sp.getInt(\"liquid_folder_corner_radius\", 0);\n"
    "        SharedPreferences.Editor e = sp.edit();\n"
    "        boolean changed = false;\n"
    "        if (!sp.contains(\"liquid_small_folder_glass\")) {\n"
    "            e.putBoolean(\"liquid_small_folder_glass\", legacyEnabled); changed = true;\n"
    "        }\n"
    "        if (!sp.contains(\"liquid_large_folder_glass\")) {\n"
    "            e.putBoolean(\"liquid_large_folder_glass\", legacyEnabled); changed = true;\n"
    "        }\n"
    "        if (!sp.contains(\"liquid_small_folder_corner_radius\")) {\n"
    "            e.putInt(\"liquid_small_folder_corner_radius\", legacyRadius); changed = true;\n"
    "        }\n"
    "        if (!sp.contains(\"liquid_large_folder_corner_radius\")) {\n"
    "            e.putInt(\"liquid_large_folder_corner_radius\", legacyRadius); changed = true;\n"
    "        }\n"
    "        if (changed) e.commit();\n"
    "    }\n\n"
    "    private static void migrateAxisDistances(SharedPreferences sp) {\n",
)

PREFS = "src/main/res/xml/preferences.xml"
replace_regex(
    PREFS,
    r'''        <SwitchPreference\n            android:key="liquid_folder_glass".*?        <SwitchPreference\n            android:key="liquid_icon_glass".*?            android:dependency="liquid_glass" />\n''',
    '''        <SwitchPreference\n            android:key="liquid_icon_glass"\n            android:title="Icon Glass"\n            android:summary="Controls both Workspace and Dock icon glass"\n            android:defaultValue="true" android:dependency="liquid_glass" />\n        <com.hellovoid.liquiddock.SeekBarPreference android:key="liquid_icon_size_offset"\n            android:title="Icon Size Offset" android:summary="%d dp per edge"\n            android:defaultValue="0" app:min="-40" app:max="40" android:dependency="liquid_icon_glass" />\n        <com.hellovoid.liquiddock.SeekBarPreference android:key="liquid_icon_corner_radius"\n            android:title="Icon Corner Radius" android:summary="%d dp (0 = Auto)"\n            android:defaultValue="0" app:min="0" app:max="128" android:dependency="liquid_icon_glass" />\n\n        <SwitchPreference android:key="liquid_widget_glass" android:title="Widget Glass"\n            android:summary="Shared glass behind widget content" android:defaultValue="true" android:dependency="liquid_glass" />\n        <com.hellovoid.liquiddock.SeekBarPreference android:key="liquid_widget_size_offset"\n            android:title="Widget Size Offset" android:summary="%d dp per edge"\n            android:defaultValue="0" app:min="-40" app:max="40" android:dependency="liquid_widget_glass" />\n        <com.hellovoid.liquiddock.SeekBarPreference android:key="liquid_widget_corner_radius"\n            android:title="Widget Corner Radius" android:summary="%d dp (0 = Auto)"\n            android:defaultValue="0" app:min="0" app:max="128" android:dependency="liquid_widget_glass" />\n\n        <SwitchPreference android:key="liquid_small_folder_glass" android:title="Small Folder Glass"\n            android:summary="1x1 folder material; miniature preview remains native" android:defaultValue="true" android:dependency="liquid_glass" />\n        <com.hellovoid.liquiddock.SeekBarPreference android:key="liquid_small_folder_size_offset"\n            android:title="Small Folder Size Offset" android:summary="%d dp per edge"\n            android:defaultValue="0" app:min="-40" app:max="40" android:dependency="liquid_small_folder_glass" />\n        <com.hellovoid.liquiddock.SeekBarPreference android:key="liquid_small_folder_corner_radius"\n            android:title="Small Folder Corner Radius" android:summary="%d dp (0 = Auto)"\n            android:defaultValue="0" app:min="0" app:max="128" android:dependency="liquid_small_folder_glass" />\n\n        <SwitchPreference android:key="liquid_large_folder_glass" android:title="Large Folder Glass"\n            android:summary="Large folder material" android:defaultValue="true" android:dependency="liquid_glass" />\n        <com.hellovoid.liquiddock.SeekBarPreference android:key="liquid_large_folder_size_offset"\n            android:title="Large Folder Size Offset" android:summary="%d dp per edge"\n            android:defaultValue="0" app:min="-40" app:max="40" android:dependency="liquid_large_folder_glass" />\n        <com.hellovoid.liquiddock.SeekBarPreference android:key="liquid_large_folder_corner_radius"\n            android:title="Large Folder Corner Radius" android:summary="%d dp (0 = Auto)"\n            android:defaultValue="0" app:min="0" app:max="128" android:dependency="liquid_large_folder_glass" />\n''',
    flags=re.S,
)

COMPOSE = "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"
replace_once(
    COMPOSE,
    "private val folderCornerRadiusSpec = IntSpec(\n"
    "    ConfigSchema.Glass.FOLDER_CORNER_RADIUS,\n"
    "    \"文件夹圆角\",\n"
    "    \"dp\",\n"
    ")\n",
    "private val iconSizeOffsetSpec = IntSpec(ConfigSchema.Glass.ICON_SIZE_OFFSET, \"图标尺寸偏移\", \"dp/边\")\n"
    "private val iconCornerRadiusSpec = IntSpec(ConfigSchema.Glass.ICON_CORNER_RADIUS, \"图标圆角\", \"dp\")\n"
    "private val widgetSizeOffsetSpec = IntSpec(ConfigSchema.Glass.WIDGET_SIZE_OFFSET, \"小部件尺寸偏移\", \"dp/边\")\n"
    "private val widgetCornerRadiusSpec = IntSpec(ConfigSchema.Glass.WIDGET_CORNER_RADIUS, \"小部件圆角\", \"dp\")\n"
    "private val smallFolderSizeOffsetSpec = IntSpec(ConfigSchema.Glass.SMALL_FOLDER_SIZE_OFFSET, \"小文件夹尺寸偏移\", \"dp/边\")\n"
    "private val smallFolderCornerRadiusSpec = IntSpec(ConfigSchema.Glass.SMALL_FOLDER_CORNER_RADIUS, \"小文件夹圆角\", \"dp\")\n"
    "private val largeFolderSizeOffsetSpec = IntSpec(ConfigSchema.Glass.LARGE_FOLDER_SIZE_OFFSET, \"大文件夹尺寸偏移\", \"dp/边\")\n"
    "private val largeFolderCornerRadiusSpec = IntSpec(ConfigSchema.Glass.LARGE_FOLDER_CORNER_RADIUS, \"大文件夹圆角\", \"dp\")\n",
)
replace_regex(
    COMPOSE,
    r'''    var liquidGlass by remember \{ mutableStateOf\(prefs\.getBoolean\(ConfigSchema\.Glass\.ENABLED\.name\(\), ConfigSchema\.Glass\.ENABLED\.uiDefault\(\)\)\) \}\n    var folderGlass by remember \{ mutableStateOf\(prefs\.getBoolean\(ConfigSchema\.Glass\.FOLDER_GLASS\.name\(\), ConfigSchema\.Glass\.FOLDER_GLASS\.uiDefault\(\)\)\) \}\n    SettingsList\((.*?)        ArrowPreference\(''',
    '''    var liquidGlass by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.ENABLED.name(), ConfigSchema.Glass.ENABLED.uiDefault())) }\n    var iconGlass by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.ICON_GLASS.name(), ConfigSchema.Glass.ICON_GLASS.uiDefault())) }\n    var widgetGlass by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.WIDGET_GLASS.name(), ConfigSchema.Glass.WIDGET_GLASS.uiDefault())) }\n    var smallFolderGlass by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.SMALL_FOLDER_GLASS.name(), ConfigSchema.Glass.SMALL_FOLDER_GLASS.uiDefault())) }\n    var largeFolderGlass by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.LARGE_FOLDER_GLASS.name(), ConfigSchema.Glass.LARGE_FOLDER_GLASS.uiDefault())) }\n    SettingsList(\1        BooleanSetting(prefs, ConfigSchema.Glass.ICON_GLASS, "图标玻璃", "同时控制桌面与 Dock 图标；0 圆角为 Auto", masterEnabled && liquidGlass) { iconGlass = it }\n        IntSetting(prefs, iconSizeOffsetSpec, masterEnabled && liquidGlass && iconGlass)\n        IntSetting(prefs, iconCornerRadiusSpec, masterEnabled && liquidGlass && iconGlass)\n        BooleanSetting(prefs, ConfigSchema.Glass.WIDGET_GLASS, "小部件玻璃", "只替换材质背景，保留 RemoteViews / MAML 内容", masterEnabled && liquidGlass) { widgetGlass = it }\n        IntSetting(prefs, widgetSizeOffsetSpec, masterEnabled && liquidGlass && widgetGlass)\n        IntSetting(prefs, widgetCornerRadiusSpec, masterEnabled && liquidGlass && widgetGlass)\n        BooleanSetting(prefs, ConfigSchema.Glass.SMALL_FOLDER_GLASS, "小文件夹玻璃", "保留 1x1 文件夹缩略预览", masterEnabled && liquidGlass) { smallFolderGlass = it }\n        IntSetting(prefs, smallFolderSizeOffsetSpec, masterEnabled && liquidGlass && smallFolderGlass)\n        IntSetting(prefs, smallFolderCornerRadiusSpec, masterEnabled && liquidGlass && smallFolderGlass)\n        BooleanSetting(prefs, ConfigSchema.Glass.LARGE_FOLDER_GLASS, "大文件夹玻璃", "独立控制大文件夹材质", masterEnabled && liquidGlass) { largeFolderGlass = it }\n        IntSetting(prefs, largeFolderSizeOffsetSpec, masterEnabled && liquidGlass && largeFolderGlass)\n        IntSetting(prefs, largeFolderCornerRadiusSpec, masterEnabled && liquidGlass && largeFolderGlass)\n        ArrowPreference(''',
    flags=re.S,
)

# ---------------------------------------------------------------------------
# Dock: rotation replaces the producer generation; body + icons share one Prismal batch/swap
# ---------------------------------------------------------------------------
DOCK_VIEW = "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java"
replace_once(
    DOCK_VIEW,
    "    private final AtomicBoolean frameAvailable = new AtomicBoolean(false);\n",
    "    private final AtomicBoolean frameAvailable = new AtomicBoolean(false);\n"
    "    private final DockGlassCompositor dockCompositor;\n",
)
replace_once(
    DOCK_VIEW,
    "        materialHostRef = new WeakReference<>(materialHost);\n",
    "        materialHostRef = new WeakReference<>(materialHost);\n"
    "        dockCompositor = new DockGlassCompositor(materialHost);\n",
)
replace_once(
    DOCK_VIEW,
    "        rightSamplingExtraPx = glassConfig.samplingExtraRightPx;\n"
    "        updateBackdropMapping();\n",
    "        rightSamplingExtraPx = glassConfig.samplingExtraRightPx;\n"
    "        dockCompositor.setIconStyle(glassConfig.iconStyle);\n"
    "        updateBackdropMapping();\n",
)
replace_once(
    DOCK_VIEW,
    "    void rebindProducer(String reason) {\n",
    "    // Dock producer remains continuous; a geometry generation replaces its BufferQueue.\n"
    "    void replaceProducerGeneration(String reason) {\n"
    "        rebindProducer(reason);\n"
    "    }\n\n"
    "    void rebindProducer(String reason) {\n",
)
replace_once(
    DOCK_VIEW,
    "            if (staleProducer != null) staleProducer.release();\n"
    "            if (staleInput != null) {\n"
    "                try { staleInput.setOnFrameAvailableListener(null); } catch (Throwable ignored) {}\n"
    "                staleInput.release();\n"
    "            }\n",
    "            releaseInputProducer(staleProducer, staleInput);\n",
)
replace_once(
    DOCK_VIEW,
    "    void shutdown() {\n",
    "    private void releaseInputProducer(Surface producer, SurfaceTexture input) {\n"
    "        if (producer != null) producer.release();\n"
    "        if (input != null) {\n"
    "            try { input.setOnFrameAvailableListener(null); } catch (Throwable ignored) {}\n"
    "            input.release();\n"
    "        }\n"
    "    }\n\n"
    "    void shutdown() {\n",
)
# Replace in-place geometry mutation with a full producer generation replacement.
replace_regex(
    DOCK_VIEW,
    r'''        configRotation = geometry\.configRotation;\n        boundSurfaceWidth = geometry\.surfaceWidth;.*?        MainHook\.log\(TAG \+ " producer geometry updated in place surface="\n                \+ geometry\.surfaceWidth \+ "x" \+ geometry\.surfaceHeight\n                \+ " buffer=" \+ geometry\.bufferWidth \+ "x" \+ geometry\.bufferHeight\n                \+ " configRot=" \+ geometry\.configRotation\);''',
    '''        replaceProducerGeneration("producer-generation-changed");''',
    flags=re.S,
)
replace_once(
    DOCK_VIEW,
    "            renderNormalizationPass(mapping);\n"
    "            PrismalGeometry prismalGeometry = createPrismalGeometry(mapping);\n"
    "            int prismalTexture = prismalRenderer.render(\n"
    "                    rawTexture, prismalGeometry, mapping.prismalParams);\n"
    "            renderCompositePass(prismalTexture, mapping);\n",
    "            renderNormalizationPass(mapping);\n"
    "            PrismalGeometry prismalGeometry = createPrismalGeometry(mapping);\n"
    "            prismalRenderer.prepareBackdrop(rawTexture, mapping.sampleWidth,\n"
    "                    mapping.sampleHeight, mapping.prismalParams);\n"
    "            float sampleInsetLeft = mapping.dockUvLeft * mapping.sampleWidth;\n"
    "            float sampleInsetTop = (1f - mapping.dockUvBottom - mapping.dockUvHeight)\n"
    "                    * mapping.sampleHeight;\n"
    "            float scaleX = (mapping.dockUvWidth * mapping.sampleWidth)\n"
    "                    / Math.max(1f, mapping.visibleWidth);\n"
    "            float scaleY = (mapping.dockUvHeight * mapping.sampleHeight)\n"
    "                    / Math.max(1f, mapping.visibleHeight);\n"
    "            dockCompositor.drawFrame(prismalRenderer, prismalGeometry, mapping.prismalParams,\n"
    "                    mapping.sampleWidth, mapping.sampleHeight, sampleInsetLeft, sampleInsetTop,\n"
    "                    scaleX, scaleY);\n"
    "            int prismalTexture = prismalRenderer.outputTexture();\n"
    "            renderCompositePass(prismalTexture, mapping);\n",
)

# ZeroCopyRenderer already exposes rebindProducer; keep the semantic handoff explicit.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307ZeroCopyRenderer.java",
    "        if (gpuBackdrop != null) gpuBackdrop.rebindProducer(reason);\n",
    "        if (gpuBackdrop != null) gpuBackdrop.replaceProducerGeneration(reason);\n",
)

print("glass scene architecture patch applied")
