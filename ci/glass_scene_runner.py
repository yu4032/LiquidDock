from pathlib import Path
import subprocess

path = Path(__file__).with_name("glass_scene_apply.py")
text = path.read_text()

obsolete_blocks = [
'''replace_once(
    SESSION,
    "        boolean rootGeometryChanged = width != rootWidth || height != rootHeight;\\n",
    "        boolean rootGeometryChanged = width != rootWidth || height != rootHeight;\\n",
)
''',
'''replace_once(
    SESSION,
    "                LauncherGlassGeometry.Snapshot selected = state.geometryStability.select(observed);\\n",
    "                LauncherGlassGeometry.Snapshot selected = rootGeometryChanged\\n"
    "                        ? observed : state.geometryStability.select(observed);\\n",
)
''',
'''replace_once(
    SESSION,
    "            input.updateTexImage();\\n"
    "            input.getTransformMatrix(textureMatrix);\\n"
    "            hasConsumedFrame = true;\\n"
    "            sourceChanged = true;\\n",
    "            input.updateTexImage();\\n"
    "            input.getTransformMatrix(textureMatrix);\\n"
    "            hasConsumedFrame = true;\\n"
    "            consumedGeneration = sceneGeneration;\\n"
    "            sourceChanged = true;\\n",
)
''',
'''replace_once(
    SESSION,
    "        renderScene(staticSnapshot, nodeSnapshot);\\n"
    "        framePolicy.onRendered();\\n",
    "        renderScene(staticSnapshot, nodeSnapshot);\\n"
    "        long renderedGeneration = consumedGeneration;\\n"
    "        if (sourceChanged && staticOutput != null && renderedGeneration == sceneGeneration) {\\n"
    "            View root = rootRef.get();\\n"
    "            mainHandler.post(() -> LauncherGlassSceneController.onFreshFrameRendered(\\n"
    "                    root, renderedGeneration));\\n"
    "        }\\n"
    "        framePolicy.onRendered();\\n",
)
''',
]
for index, block in enumerate(obsolete_blocks):
    expected = 2 if index == 1 else 1
    actual = text.count(block)
    if actual != expected:
        raise RuntimeError(f"obsolete patch block {index} count={actual}, expected={expected}")
    text = text.replace(block, "", expected)
path.write_text(text)

subprocess.run(["python3", str(path)], check=True)

root = Path(__file__).resolve().parents[1]
session = root / "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java"
source = session.read_text()
replacements = [
(
'''            LauncherGlassGeometry.Snapshot selected =
                    node.geometryStability.select(old, observed, localChanged);''',
'''            LauncherGlassGeometry.Snapshot selected = rootGeometryChanged
                    ? observed : node.geometryStability.select(old, observed, localChanged);'''
),
(
'''            LauncherGlassGeometry.Snapshot selected =
                    state.geometryStability.select(old, observed, localChanged);''',
'''            LauncherGlassGeometry.Snapshot selected = rootGeometryChanged
                    ? observed : state.geometryStability.select(old, observed, localChanged);'''
),
(
'''                input.updateTexImage();
                input.getTransformMatrix(textureMatrix);
                hasConsumedFrame = true;
                sourceChanged = true;''',
'''                input.updateTexImage();
                input.getTransformMatrix(textureMatrix);
                hasConsumedFrame = true;
                consumedGeneration = sceneGeneration;
                sourceChanged = true;'''
),
(
'''            if (!hasConsumedFrame) return;
            renderScene(work.rebuildBackdrop || sourceChanged || !backdropPrepared);''',
'''            if (!hasConsumedFrame) return;
            renderScene(work.rebuildBackdrop || sourceChanged || !backdropPrepared);
            long renderedGeneration = consumedGeneration;
            if (sourceChanged && staticOutput != null && renderedGeneration == sceneGeneration) {
                View rootView = rootRef.get();
                mainHandler.post(() -> LauncherGlassSceneController.onFreshFrameRendered(
                        rootView, renderedGeneration));
            }'''
),
]
for old, new in replacements:
    if source.count(old) != 1:
        raise RuntimeError(f"current Session post-patch pattern count={source.count(old)}: {old[:60]!r}")
    source = source.replace(old, new, 1)
session.write_text(source)
