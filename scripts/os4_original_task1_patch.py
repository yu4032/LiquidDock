from pathlib import Path

PARAMS = Path('prismal/src/main/java/com/hellovoid/prismal/PrismalParams.java')
RENDERER = Path('prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one anchor, found {count}')
    return text.replace(old, new, 1)

p = PARAMS.read_text()
p = replace_once(p,
    '    public final float highlightWidth;\n',
    '    public final float highlightWidth;\n'
    '    public final float os4EdgeWidthPx;\n'
    '    public final float os4ReflectOffsetPx;\n'
    '    public final float os4ReflectionStrength;\n'
    '    public final float os4ReflectionLighten;\n'
    '    public final float os4DirectionalAngleRange;\n'
    '    public final float os4DirectionalIntensity;\n'
    '    public final float os4DirectionalOppositeIntensity;\n',
    'params fields')
p = replace_once(p,
    '        highlightWidth = b.highlightWidth;\n',
    '        highlightWidth = b.highlightWidth;\n'
    '        os4EdgeWidthPx = b.os4EdgeWidthPx;\n'
    '        os4ReflectOffsetPx = b.os4ReflectOffsetPx;\n'
    '        os4ReflectionStrength = b.os4ReflectionStrength;\n'
    '        os4ReflectionLighten = b.os4ReflectionLighten;\n'
    '        os4DirectionalAngleRange = b.os4DirectionalAngleRange;\n'
    '        os4DirectionalIntensity = b.os4DirectionalIntensity;\n'
    '        os4DirectionalOppositeIntensity = b.os4DirectionalOppositeIntensity;\n',
    'params constructor')
p = replace_once(p,
    '        public float highlightWidth = 4f;\n',
    '        public float highlightWidth = 4f;\n'
    '        // OS4 runtime values were not recovered; these are bounded implementation defaults.\n'
    '        public float os4EdgeWidthPx = 0f;\n'
    '        public float os4ReflectOffsetPx = 0f;\n'
    '        public float os4ReflectionStrength = 0.28f;\n'
    '        public float os4ReflectionLighten = 0.16f;\n'
    '        public float os4DirectionalAngleRange = 0.52f;\n'
    '        public float os4DirectionalIntensity = 0.42f;\n'
    '        public float os4DirectionalOppositeIntensity = 0.14f;\n',
    'params builder')
PARAMS.write_text(p)

r = RENDERER.read_text()
r = replace_once(r,
    '        uniform1f("u_highlightWidth", p.highlightWidth);\n',
    '        uniform1f("u_highlightWidth", p.highlightWidth);\n'
    '        uniform1f("u_os4EdgeWidthPx", p.os4EdgeWidthPx);\n'
    '        uniform1f("u_os4ReflectOffsetPx", p.os4ReflectOffsetPx);\n'
    '        uniform1f("u_os4ReflectionStrength", p.os4ReflectionStrength);\n'
    '        uniform1f("u_os4ReflectionLighten", p.os4ReflectionLighten);\n'
    '        uniform1f("u_os4DirectionalAngleRange", p.os4DirectionalAngleRange);\n'
    '        uniform1f("u_os4DirectionalIntensity", p.os4DirectionalIntensity);\n'
    '        uniform1f("u_os4DirectionalOppositeIntensity", p.os4DirectionalOppositeIntensity);\n',
    'renderer uniforms')
RENDERER.write_text(r)
