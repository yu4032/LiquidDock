from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(rel, old, new):
    path = ROOT / rel
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{rel}: expected one match, got {count}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1))


SESSION = "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java"
VIEW = "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java"

# Workspace pre-draw remains the lightweight change detector, but unchanged nodes must never enter
# expensive root/global geometry resolution. This is the idle-power gate.
replace_once(SESSION,
'''            boolean localChanged = sink.syncFromMaterial();
            dragChanged |= localChanged;
            LauncherGlassGeometry.Snapshot observed = sink.captureGeometry(root);''',
'''            boolean localChanged = sink.syncFromMaterial();
            dragChanged |= localChanged;
            if (!rootGeometryChanged && !localChanged) continue;
            LauncherGlassGeometry.Snapshot observed = sink.captureGeometry(root);''')

replace_once(SESSION,
'''            boolean localChanged = node.syncFromMaterial();
            staticChanged |= localChanged;
            LauncherGlassGeometry.Snapshot observed = node.captureGeometry(root);''',
'''            boolean localChanged = node.syncFromMaterial();
            staticChanged |= localChanged;
            if (!rootGeometryChanged && !localChanged) continue;
            LauncherGlassGeometry.Snapshot observed = node.captureGeometry(root);''')

# Dock item geometry belongs to the independent continuous Dock layer, not the wallpaper/backdrop
# generation. The UI thread publishes Dock-local item snapshots separately; every continuous OES
# frame consumes the latest one.
replace_once(VIEW,
'''        final float dockUvWidth;
        final float dockUvHeight;
        final DockGlassSceneSnapshot dockScene;
        final Miuix307BackdropMapping.Coverage coverage;''',
'''        final float dockUvWidth;
        final float dockUvHeight;
        final Miuix307BackdropMapping.Coverage coverage;''')

replace_once(VIEW,
'''                float validDockLeft, float validDockBottom,
                float validDockRight, float validDockTop,
                float dockUvLeft, float dockUvBottom, float dockUvWidth, float dockUvHeight,
                DockGlassSceneSnapshot dockScene,
                Miuix307BackdropMapping.Coverage coverage) {''',
'''                float validDockLeft, float validDockBottom,
                float validDockRight, float validDockTop,
                float dockUvLeft, float dockUvBottom, float dockUvWidth, float dockUvHeight,
                Miuix307BackdropMapping.Coverage coverage) {''')

replace_once(VIEW,
'''            this.dockUvWidth = dockUvWidth;
            this.dockUvHeight = dockUvHeight;
            this.dockScene = dockScene != null ? dockScene : DockGlassSceneSnapshot.EMPTY;
            this.coverage = coverage;''',
'''            this.dockUvWidth = dockUvWidth;
            this.dockUvHeight = dockUvHeight;
            this.coverage = coverage;''')

replace_once(VIEW,
'''            prismalRenderer.prepareBackdrop(rawTexture, mapping.sampleWidth,
                    mapping.sampleHeight, mapping.prismalParams);
            DockGlassSceneSnapshot dockScene = mapping.dockScene;
            dockCompositor.drawFrame(prismalRenderer, prismalGeometry, mapping.prismalParams,
                    dockScene, mapping.sampleWidth, mapping.sampleHeight);''',
'''            prismalRenderer.prepareBackdrop(rawTexture, mapping.sampleWidth,
                    mapping.sampleHeight, mapping.prismalParams);
            DockGlassSceneSnapshot dockScene = dockCompositor.latestScene();
            dockCompositor.drawFrame(prismalRenderer, prismalGeometry, mapping.prismalParams,
                    dockScene, mapping.sampleWidth, mapping.sampleHeight);''')

replace_once(VIEW,
'''        DockGlassSceneSnapshot nextDockScene = dockCompositor.captureUiSnapshot(
                sampleWidth, sampleHeight, dockSampleInsetLeft, dockSampleInsetTop,
                dockScaleX, dockScaleY);

        BackdropSnapshot currentSnapshot = backdropSnapshot;''',
'''        dockCompositor.refreshUiSceneIfNeeded(
                sampleWidth, sampleHeight, dockSampleInsetLeft, dockSampleInsetTop,
                dockScaleX, dockScaleY);

        BackdropSnapshot currentSnapshot = backdropSnapshot;''')

replace_once(VIEW,
'''                && Float.compare(dockUvBottom, nextDockUvBottom) == 0
                && Float.compare(dockUvWidth, nextDockUvWidth) == 0
                && Float.compare(dockUvHeight, nextDockUvHeight) == 0
                && currentSnapshot.dockScene.sameAs(nextDockScene)
                && producerCoverage == dock.coverage;''',
'''                && Float.compare(dockUvBottom, nextDockUvBottom) == 0
                && Float.compare(dockUvWidth, nextDockUvWidth) == 0
                && Float.compare(dockUvHeight, nextDockUvHeight) == 0
                && producerCoverage == dock.coverage;''')

replace_once(VIEW,
'''                dock.validLeft, dock.validBottom, dock.validRight, dock.validTop,
                nextDockUvLeft, nextDockUvBottom, nextDockUvWidth, nextDockUvHeight,
                nextDockScene, dock.coverage);''',
'''                dock.validLeft, dock.validBottom, dock.validRight, dock.validTop,
                nextDockUvLeft, nextDockUvBottom, nextDockUvWidth, nextDockUvHeight,
                dock.coverage);''')

print("idle Workspace + continuous Dock scene fix applied")
