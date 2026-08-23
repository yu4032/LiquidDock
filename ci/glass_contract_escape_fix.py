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

# The compact method rewriter intentionally replaces a whole final @Test block. In this legacy
# class a shared helper follows that test, so restore the original helper if the rewrite consumed it.
path = ROOT / "src/test/java/com/hellovoid/liquiddock/Miuix307GlassCustomizationContractTest.java"
text = path.read_text()
helper = '''
    private static int occurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }
'''
if "private static int occurrences(String text, String needle)" not in text:
    close = text.rfind("\n}")
    if close < 0:
        raise RuntimeError("Miuix307GlassCustomizationContractTest: class close not found")
    text = text[:close] + helper + text[close:]
    path.write_text(text)

# Dock item nodes may mention the parent TextureView in documentation without owning one.  Assert
# actual resource ownership constructs rather than matching that harmless word in a comment.
path = ROOT / "src/test/java/com/hellovoid/liquiddock/DockGlassSceneContractTest.java"
text = path.read_text()
bad_dock = 'assertFalse(n.contains("TextureView")||n.contains("EGLSurface")||n.contains("LauncherGlassStaticNode"));'
good_dock = 'assertFalse(n.contains("extends TextureView")||n.contains("new TextureView")||n.contains("EGLSurface")||n.contains("LauncherGlassStaticNode"));'
if text.count(bad_dock) != 1:
    raise RuntimeError(f"DockGlassSceneContractTest: broad ownership assertion count={text.count(bad_dock)}")
path.write_text(text.replace(bad_dock, good_dock, 1))
print("generated Scheme A contract repairs applied")
