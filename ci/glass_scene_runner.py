from pathlib import Path
import subprocess

path = Path(__file__).with_name("glass_scene_apply.py")
text = path.read_text()

blocks = [
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
]
for block in blocks:
    if block not in text:
        raise RuntimeError("expected obsolete geometry stability patch block not found")
    text = text.replace(block, "", 1)
# The obsolete state block appears twice in the original script.
second = blocks[1]
if second not in text:
    raise RuntimeError("expected second obsolete geometry stability patch block not found")
text = text.replace(second, "", 1)
path.write_text(text)

subprocess.run(["python3", str(path)], check=True)

session = Path(__file__).resolve().parents[1] / "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java"
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
]
for old, new in replacements:
    if source.count(old) != 1:
        raise RuntimeError(f"current stability API patch count={source.count(old)}")
    source = source.replace(old, new, 1)
session.write_text(source)
