from pathlib import Path

path = Path('prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java')
text = path.read_text()
anchor = '        uniform1f("u_highlightWidth", p.highlightWidth);\n'
replacement = anchor + (
    '        uniform1f("u_os4EdgeWidthPx", p.os4EdgeWidthPx);\n'
    '        uniform1f("u_os4ThicknessPx", p.os4ThicknessPx);\n'
    '        uniform1f("u_os4ReflectOffsetPx", p.os4ReflectOffsetPx);\n'
    '        uniform1f("u_os4BloomWidthPx", p.os4BloomWidthPx);\n'
    '        uniform1f("u_os4BloomIntensity", p.os4BloomIntensity);\n'
    '        uniform1f("u_os4BloomInMainPass", p.os4BloomEnabled ? 0f : 1f);\n'
)
if text.count(anchor) != 1:
    raise SystemExit(f'expected exactly one renderer highlight-width anchor, found {text.count(anchor)}')
if 'u_os4EdgeWidthPx' in text:
    raise SystemExit('OS4 renderer uniforms already present')
path.write_text(text.replace(anchor, replacement))
