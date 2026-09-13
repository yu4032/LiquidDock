from pathlib import Path


def replace_all(path, old, new):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f'anchor not found in {path}: {old!r}')
    p.write_text(text.replace(old, new))


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f'anchor not found in {path}: {old[:140]!r}')
    p.write_text(text.replace(old, new, 1))

# Dock candidate membership/rendering must stay alive when functional-only is the sole icon mode.
replace_all(
    'src/main/java/com/hellovoid/liquiddock/DockGlassItemRegistry.java',
    'GlassRuntimeState.isIconEnabled()',
    'GlassRuntimeState.isAnyIconEnabled()')

# The broad icon flag remains distinct, but the shared Dock icon style must be enabled whenever
# either broad or functional-only icon glass can render. Workspace ownership is still gated by
# GlassRuntimeState.isIconEnabled() in MiuixLauncherStaticGlassHook.
replace_once(
    'src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java',
    '''            iconStyle = new GlassComponentStyle(resolvedIconEnabled,\n                    c.f(ConfigSchema.Glass.ICON_SIZE_OFFSET.name(), 0f),\n                    c.f(ConfigSchema.Glass.ICON_CORNER_RADIUS.name(), 0f));\n''',
    '''            iconStyle = new GlassComponentStyle(\n                    resolvedIconEnabled || functionalDockIconEnabled,\n                    c.f(ConfigSchema.Glass.ICON_SIZE_OFFSET.name(), 0f),\n                    c.f(ConfigSchema.Glass.ICON_CORNER_RADIUS.name(), 0f));\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java',
    '            iconEnabled = iconStyle.enabled;\n',
    '            iconEnabled = resolvedIconEnabled;\n')
