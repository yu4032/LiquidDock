#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEST = ROOT / "src/test/java/com/hellovoid/liquiddock"


def replace_once(name, old, new):
    p = TEST / name
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"missing contract anchor in {name}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


# Comments may name TextureView/EGLSurface; ownership contracts must inspect actual resource syntax.
replace_once("DockContinuousLocalDomainContractTest.java",
'''        assertFalse(item.contains("SurfaceTexture"));
        assertFalse(item.contains("EGLSurface"));''',
'''        assertFalse(item.contains("new SurfaceTexture("));
        assertFalse(item.contains("EGLSurface "));
        assertFalse(item.contains("extends TextureView"));''')
replace_once("DockGlassSceneContractTest.java",
'''        assertFalse(item.contains("TextureView"));
        assertFalse(item.contains("SurfaceTexture"));
        assertFalse(item.contains("EGLSurface"));''',
'''        assertFalse(item.contains("extends TextureView"));
        assertFalse(item.contains("new TextureView("));
        assertFalse(item.contains("new SurfaceTexture("));
        assertFalse(item.contains("EGLSurface "));''')
replace_once("LauncherGlassDomainOwnershipContractTest.java",
'''        assertFalse(item.contains("SurfaceTexture"));
        assertFalse(item.contains("EGLSurface"));
        assertFalse(item.contains("extends TextureView"));''',
'''        assertFalse(item.contains("new SurfaceTexture("));
        assertFalse(item.contains("EGLSurface "));
        assertFalse(item.contains("extends TextureView"));''')

# The Dock still owns exactly one TextureView. The new item compositor is intentionally inside it.
replace_once("LauncherGlassCoverageAndSurfaceContractTest.java",
'''        assertTrue(dock.contains("extends TextureView"));
        assertFalse(dock.contains("DockGlassItemNode"));
        assertFalse(dock.contains("DockGlassCompositor"));''',
'''        assertTrue(dock.contains("extends TextureView"));
        assertTrue(dock.contains("DockGlassCompositor"));
        assertTrue(dock.contains("dockCompositor.drawFrame("));
        assertFalse(dock.contains("new DockGlassItemNode("));''')

# Main's former single-body Prismal render is now the same GPU stages plus a one-frame Dock batch:
# prepare backdrop once, draw body + items, consume the renderer output once.
triple = '''view.contains("prismalRenderer.prepareBackdrop(")
                && view.contains("dockCompositor.drawFrame(")
                && view.contains("prismalRenderer.outputTexture()")'''

replace_once("Miuix307PassBlurGpuDemoTest.java",
'''        assertTrue(view.contains("prismalRenderer.render("));''',
'''        assertTrue(''' + triple + ''');''')
replace_once("Miuix307PassBlurGpuDemoTest.java",
'''        assertTrue(view.contains("prismalRenderer.render("));''',
'''        assertTrue(''' + triple + ''');''')
replace_once("Miuix307PrismalParityRepairTest.java",
'''        assertTrue(view.contains("prismalRenderer.render("));''',
'''        assertTrue(''' + triple + ''');''')
replace_once("Miuix307TextureViewPassBlurCalibrationTest.java",
'''        assertTrue(view.contains("prismalRenderer.render("));''',
'''        assertTrue(''' + triple + ''');''')
replace_once("PrismalModuleBoundaryContractTest.java",
'''        assertTrue(view.contains("prismalRenderer.render("));''',
'''        assertTrue(''' + triple + ''');''')

replace_once("Miuix307TextureViewStrongRefractionTest.java",
'''        assertTrue(source.contains("PrismalGeometry prismalGeometry = createPrismalGeometry(mapping)")
                && source.contains("prismalRenderer.render("));''',
'''        assertTrue(source.contains("PrismalGeometry prismalGeometry = createPrismalGeometry(mapping)")
                && source.contains("prismalRenderer.prepareBackdrop(")
                && source.contains("dockCompositor.drawFrame(")
                && source.contains("prismalRenderer.outputTexture()"));''')

replace_once("Miuix307GlassCustomizationContractTest.java",
'''        assertTrue("the renderer must consume the snapshot's portable Prismal params",
                view.contains("rawTexture, prismalGeometry, mapping.prismalParams"));''',
'''        assertTrue("the renderer must consume the snapshot's portable Prismal params",
                view.contains("rawTexture, mapping.sampleWidth, mapping.sampleHeight, mapping.prismalParams")
                && view.contains("dockCompositor.drawFrame(prismalRenderer, prismalGeometry, mapping.prismalParams"));''')

print("main Dock batch contracts applied")
