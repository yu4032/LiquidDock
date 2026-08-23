from pathlib import Path
path = Path(__file__).resolve().parents[1] / "src/main/java/com/hellovoid/liquiddock/LauncherGlassDragOverlay.java"
text = path.read_text()
old = "        if (sink.syncFromMaterial()) sink.requestLifecycleRefresh();\n"
if text.count(old) != 1:
    raise RuntimeError(f"drag per-frame duplicate sync count={text.count(old)}")
path.write_text(text.replace(old, "", 1))
print("drag per-frame duplicate material sync removed")
