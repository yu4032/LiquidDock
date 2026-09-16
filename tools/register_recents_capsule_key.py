from pathlib import Path
p = Path('src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java')
text = p.read_text()
old = '''                Glass.FUNCTIONAL_DOCK_ICON_GLASS,
                Glass.FOLDER_CORNER_RADIUS,'''
new = '''                Glass.FUNCTIONAL_DOCK_ICON_GLASS, Glass.RECENTS_CAPSULE_GLASS,
                Glass.FOLDER_CORNER_RADIUS,'''
if new not in text:
    if old not in text:
        raise SystemExit('ConfigSchema registration anchor missing')
    p.write_text(text.replace(old, new, 1))
