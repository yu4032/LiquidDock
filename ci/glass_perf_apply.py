from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def replace_once(rel, old, new):
    path = ROOT / rel
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{rel}: expected one match, got {count}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1))


def replace_regex(rel, pattern, replacement):
    path = ROOT / rel
    text = path.read_text()
    next_text, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"{rel}: regex match count={count}: {pattern[:100]!r}")
    path.write_text(next_text)


SESSION = "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java"

replace_once(SESSION,
'''    void registerSink(LauncherGlassSinkView sink) {
        if (sink == null || shuttingDown) return;
        synchronized (nodes) {
            if (!nodes.containsKey(sink)) nodes.put(sink, new NodeState(sink));
        }
        syncSceneOnUiThread();
        requestLifecycleRefresh();
    }
''',
'''    void registerSink(LauncherGlassSinkView sink) {
        if (sink == null || shuttingDown) return;
        synchronized (nodes) {
            if (!nodes.containsKey(sink)) nodes.put(sink, new NodeState(sink));
        }
        syncSceneOnUiThread();
        requestDragRedraw();
    }
''')

replace_once(SESSION,
'''    void unregisterSink(LauncherGlassSinkView sink) {
        if (sink == null) return;
        synchronized (nodes) { nodes.remove(sink); }
        requestLifecycleRefresh();
    }
''',
'''    void unregisterSink(LauncherGlassSinkView sink) {
        if (sink == null) return;
        synchronized (nodes) { nodes.remove(sink); }
        requestDragRedraw();
    }
''')

replace_once(SESSION,
'''        // Interaction redraws reuse the last consumed wallpaper texture and prepared blur.
        requestFrame(false);
    }


    void registerStaticNode''',
'''        // Interaction redraws reuse the last consumed wallpaper texture and prepared blur.
        requestDragRedraw();
    }


    void registerStaticNode''')

replace_once(SESSION,
'''        syncSceneOnUiThread();
        requestSceneRedraw();
    }

    void unregisterStaticNode''',
'''        syncSceneOnUiThread();
        requestStaticRedraw();
    }

    void unregisterStaticNode''')

replace_once(SESSION,
'''    void unregisterStaticNode(LauncherGlassStaticNode node) {
        if (node == null) return;
        synchronized (staticNodes) { staticNodes.remove(node); }
        requestLifecycleRefresh();
    }
''',
'''    void unregisterStaticNode(LauncherGlassStaticNode node) {
        if (node == null) return;
        synchronized (staticNodes) { staticNodes.remove(node); }
        requestStaticRedraw();
    }
''')

replace_once(SESSION,
'''        }
        requestFrame(false);
    }

    void requestLifecycleRefresh()''',
'''        }
        requestStaticRedraw();
    }

    void requestLifecycleRefresh()''')

replace_once(SESSION,
'''    void requestSceneRedraw() {
        if (shuttingDown) return;
        requestFrame(false);
    }

    void suspendWorkspaceProducer()''',
'''    void requestSceneRedraw() {
        if (shuttingDown) return;
        requestFrame(false);
    }

    void requestDragRedraw() {
        if (shuttingDown) return;
        if (framePolicy.requestDrag()) postRender(this::drainFrameWork, null);
    }

    void requestStaticRedraw() {
        if (shuttingDown) return;
        if (framePolicy.requestStatic()) postRender(this::drainFrameWork, null);
    }

    void suspendWorkspaceProducer()''')

replace_regex(SESSION,
r'''    private void syncSceneOnUiThread\(\) \{.*?\n    \}\n\n    private boolean rebuildAtlasLayout''',
'''    private void syncSceneOnUiThread() {
        if (shuttingDown) return;
        View root = rootRef.get();
        if (root == null) return;
        int nextWidth = root.getWidth();
        int nextHeight = root.getHeight();
        boolean rootGeometryChanged = nextWidth > 0 && nextHeight > 0
                && (nextWidth != rootWidth || nextHeight != rootHeight);
        boolean dragChanged = rootGeometryChanged;
        boolean staticChanged = rootGeometryChanged;
        if (nextWidth > 0) rootWidth = nextWidth;
        if (nextHeight > 0) rootHeight = nextHeight;

        List<NodeState> dragSnapshot;
        synchronized (nodes) { dragSnapshot = new ArrayList<>(nodes.values()); }
        for (NodeState node : dragSnapshot) {
            LauncherGlassSinkView sink = node.sinkRef.get();
            if (sink == null) continue;
            boolean localChanged = sink.syncFromMaterial();
            dragChanged |= localChanged;
            LauncherGlassGeometry.Snapshot observed = sink.captureGeometry(root);
            LauncherGlassGeometry.Snapshot old = node.geometry;
            LauncherGlassGeometry.Snapshot selected = rootGeometryChanged
                    ? observed : node.geometryStability.select(old, observed, localChanged);
            if ((old == null) != (selected == null)
                    || (old != null && !old.sameAs(selected))) {
                node.geometry = selected;
                dragChanged = true;
            }
        }

        List<StaticNodeState> staticSnapshot;
        synchronized (staticNodes) { staticSnapshot = new ArrayList<>(staticNodes.values()); }
        for (StaticNodeState state : staticSnapshot) {
            LauncherGlassStaticNode node = state.nodeRef.get();
            if (node == null) continue;
            boolean localChanged = node.syncFromMaterial();
            staticChanged |= localChanged;
            LauncherGlassGeometry.Snapshot observed = node.captureGeometry(root);
            LauncherGlassGeometry.Snapshot old = state.geometry;
            LauncherGlassGeometry.Snapshot selected = rootGeometryChanged
                    ? observed : state.geometryStability.select(old, observed, localChanged);
            if ((old == null) != (selected == null)
                    || (old != null && !old.sameAs(selected))) {
                state.geometry = selected;
                staticChanged = true;
            }
        }

        boolean producerGeometryChanged = refreshProducerGeometryOnUi(root);
        if (producerGeometryChanged || rootGeometryChanged) {
            requestBackdropRebuild();
            return;
        }
        boolean schedule = false;
        if (staticChanged) schedule |= framePolicy.requestStatic();
        if (dragChanged) schedule |= framePolicy.requestDrag();
        if (schedule) postRender(this::drainFrameWork, null);
    }

    private boolean rebuildAtlasLayout''')

replace_once(SESSION,
'''            if (!hasConsumedFrame) return;
            renderScene(work.rebuildBackdrop || sourceChanged || !backdropPrepared);
            long renderedGeneration = consumedGeneration;''',
'''            if (!hasConsumedFrame) return;
            boolean backdropDirty = work.rebuildBackdrop || sourceChanged || !backdropPrepared;
            boolean staticDirty = backdropDirty;
            if (work.staticDirty) staticDirty = true;
            boolean dragDirty = backdropDirty;
            if (work.dragDirty) dragDirty = true;
            renderScene(backdropDirty, staticDirty, dragDirty);
            long renderedGeneration = consumedGeneration;''')

replace_once(SESSION,
'''    private void renderScene(boolean rebuildBackdrop) {
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
''',
'''    private void renderScene(boolean rebuildBackdrop, boolean renderStatic, boolean renderDrag) {
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
        if (renderStatic) renderStaticScene(params);
        if (renderDrag) renderDragOutputs(params);
    }
''')

# Lightweight node lifecycle invalidations must enter the correct render domain.
replace_once("src/main/java/com/hellovoid/liquiddock/LauncherGlassSinkView.java",
'''    void requestLifecycleRefresh() {
        LauncherGlassSession live = ensureLiveSession();
        if (!disposed && live != null) live.requestLifecycleRefresh();
    }
''',
'''    void requestLifecycleRefresh() {
        LauncherGlassSession live = ensureLiveSession();
        if (!disposed && live != null) live.requestDragRedraw();
    }
''')

replace_once("src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java",
'''    void requestLifecycleRefresh() {
        if (disposed) return;
        LauncherGlassSession live = ensureLiveSession();
        if (live != null) live.requestLifecycleRefresh();
    }
''',
'''    void requestLifecycleRefresh() {
        if (disposed) return;
        LauncherGlassSession live = ensureLiveSession();
        if (live != null) live.requestStaticRedraw();
    }
''')

# Every ShortcutIcon registers once; Dock snapshots iterate weak candidates, never the whole View tree.
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java",
'''    private static void observeHost(
            View host, LauncherGlassDragState.Kind kind, LiquidDockConfig.Glass glassConfig) {
        if (host == null) return;
        synchronized (BOOTSTRAP_OBSERVERS) {''',
'''    private static void observeHost(
            View host, LauncherGlassDragState.Kind kind, LiquidDockConfig.Glass glassConfig) {
        if (host == null) return;
        if (kind == LauncherGlassDragState.Kind.ICON) DockGlassItemRegistry.register(host);
        synchronized (BOOTSTRAP_OBSERVERS) {''')

VIEW = "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java"
replace_once(VIEW,
'''        final Miuix307BackdropMapping.Coverage coverage;

        BackdropSnapshot(''',
'''        final DockGlassSceneSnapshot dockScene;
        final Miuix307BackdropMapping.Coverage coverage;

        BackdropSnapshot(''')

replace_once(VIEW,
'''                float dockUvLeft, float dockUvBottom, float dockUvWidth, float dockUvHeight,
                Miuix307BackdropMapping.Coverage coverage) {''',
'''                float dockUvLeft, float dockUvBottom, float dockUvWidth, float dockUvHeight,
                DockGlassSceneSnapshot dockScene,
                Miuix307BackdropMapping.Coverage coverage) {''')

replace_once(VIEW,
'''            this.dockUvWidth = dockUvWidth;
            this.dockUvHeight = dockUvHeight;
            this.coverage = coverage;''',
'''            this.dockUvWidth = dockUvWidth;
            this.dockUvHeight = dockUvHeight;
            this.dockScene = dockScene != null ? dockScene : DockGlassSceneSnapshot.EMPTY;
            this.coverage = coverage;''')

replace_once(VIEW,
'''        float nextDockUvLeft = insets.left / (float) sampleWidth;
        float nextDockUvBottom = insets.bottom / (float) sampleHeight;
        float nextDockUvWidth = visibleWidth / (float) sampleWidth;
        float nextDockUvHeight = visibleHeight / (float) sampleHeight;

        BackdropSnapshot currentSnapshot = backdropSnapshot;''',
'''        float nextDockUvLeft = insets.left / (float) sampleWidth;
        float nextDockUvBottom = insets.bottom / (float) sampleHeight;
        float nextDockUvWidth = visibleWidth / (float) sampleWidth;
        float nextDockUvHeight = visibleHeight / (float) sampleHeight;
        float dockSampleInsetLeft = nextDockUvLeft * sampleWidth;
        float dockSampleInsetTop = (1f - nextDockUvBottom - nextDockUvHeight) * sampleHeight;
        float dockScaleX = (nextDockUvWidth * sampleWidth) / Math.max(1f, visibleWidth);
        float dockScaleY = (nextDockUvHeight * sampleHeight) / Math.max(1f, visibleHeight);
        DockGlassSceneSnapshot nextDockScene = dockCompositor.captureUiSnapshot(
                sampleWidth, sampleHeight, dockSampleInsetLeft, dockSampleInsetTop,
                dockScaleX, dockScaleY);

        BackdropSnapshot currentSnapshot = backdropSnapshot;''')

replace_once(VIEW,
'''                && Float.compare(dockUvWidth, nextDockUvWidth) == 0
                && Float.compare(dockUvHeight, nextDockUvHeight) == 0
                && producerCoverage == dock.coverage;''',
'''                && Float.compare(dockUvWidth, nextDockUvWidth) == 0
                && Float.compare(dockUvHeight, nextDockUvHeight) == 0
                && currentSnapshot.dockScene.sameAs(nextDockScene)
                && producerCoverage == dock.coverage;''')

replace_once(VIEW,
'''                dock.validLeft, dock.validBottom, dock.validRight, dock.validTop,
                nextDockUvLeft, nextDockUvBottom, nextDockUvWidth, nextDockUvHeight,
                dock.coverage);''',
'''                dock.validLeft, dock.validBottom, dock.validRight, dock.validTop,
                nextDockUvLeft, nextDockUvBottom, nextDockUvWidth, nextDockUvHeight,
                nextDockScene, dock.coverage);''')

replace_once(VIEW,
'''            float sampleInsetLeft = mapping.dockUvLeft * mapping.sampleWidth;
            float sampleInsetTop = (1f - mapping.dockUvBottom - mapping.dockUvHeight)
                    * mapping.sampleHeight;
            float scaleX = (mapping.dockUvWidth * mapping.sampleWidth)
                    / Math.max(1f, mapping.visibleWidth);
            float scaleY = (mapping.dockUvHeight * mapping.sampleHeight)
                    / Math.max(1f, mapping.visibleHeight);
            dockCompositor.drawFrame(prismalRenderer, prismalGeometry, mapping.prismalParams,
                    mapping.sampleWidth, mapping.sampleHeight, sampleInsetLeft, sampleInsetTop,
                    scaleX, scaleY);''',
'''            DockGlassSceneSnapshot dockScene = mapping.dockScene;
            dockCompositor.drawFrame(prismalRenderer, prismalGeometry, mapping.prismalParams,
                    dockScene, mapping.sampleWidth, mapping.sampleHeight);''')

print("glass performance patch applied")
