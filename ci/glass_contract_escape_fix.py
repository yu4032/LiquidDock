from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
bad = 'v.contains("replaceProducerGeneration("producer-generation-changed")")'
good = 'v.contains("replaceProducerGeneration")&&v.contains("producer-generation-changed")'
for name in [
    "Miuix307PassBlurGpuDemoTest.java",
    "Miuix307TextureViewPassBlurCalibrationTest.java",
]:
    path = ROOT / "src/test/java/com/hellovoid/liquiddock" / name
    text = path.read_text()
    if text.count(bad) != 1:
        raise RuntimeError(f"{name}: generated quote pattern count={text.count(bad)}")
    path.write_text(text.replace(bad, good, 1))
print("generated contract quote repair applied")
