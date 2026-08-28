from pathlib import Path

path = Path('src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java')
source = path.read_text()
replacements = {
    'LauncherGlassVendorMaterialSuppressor.releaseWidget(host)':
        'LauncherWidgetBackgroundController.release(host)',
    'LauncherGlassVendorMaterialSuppressor.claimWidget(host)':
        'LauncherWidgetBackgroundController.claim(host)',
}
for old, new in replacements.items():
    count = source.count(old)
    if count == 0:
        raise SystemExit(f'missing migration anchor: {old}')
    source = source.replace(old, new)
if 'LauncherGlassVendorMaterialSuppressor.releaseWidget(host)' in source:
    raise SystemExit('stale releaseWidget call remains')
if 'LauncherGlassVendorMaterialSuppressor.claimWidget(host)' in source:
    raise SystemExit('stale claimWidget call remains')
path.write_text(source)
print('widget controller call sites migrated')
