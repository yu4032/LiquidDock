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

# The compact method rewriter intentionally replaces a whole final @Test block.  In this legacy
# test class a shared helper follows the final test, so restore that helper if the rewrite consumed
# it.  Keep the helper byte-for-byte equivalent to the original contract implementation.
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
print("generated contract quote/helper repair applied")
