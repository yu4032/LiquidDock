from pathlib import Path
import subprocess

path = Path(__file__).with_name("glass_scene_apply.py")
text = path.read_text()
noop = '''replace_once(\n    SESSION,\n    "        boolean rootGeometryChanged = width != rootWidth || height != rootHeight;\\n",\n    "        boolean rootGeometryChanged = width != rootWidth || height != rootHeight;\\n",\n)\n'''
if noop not in text:
    raise RuntimeError("expected rootGeometryChanged no-op guard not found")
path.write_text(text.replace(noop, "", 1))
subprocess.run(["python3", str(path)], check=True)
