from pathlib import Path

path = Path('src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java')
text = path.read_text()
old = '''        add(keys, Grid.ENABLED, Grid.PROFILE, Grid.WIDGET_ADAPTATION,
                Grid.MARGINS_DP, Grid.MARGINS_OFFSET,
'''
new = '''        add(keys, Grid.ENABLED, Grid.PROFILE, Grid.WIDGET_ADAPTATION,
                Grid.ICON_SIZE_ENABLED, Grid.ICON_SIZE_PERCENT,
                Grid.MARGINS_DP, Grid.MARGINS_OFFSET,
'''
if 'Grid.ICON_SIZE_ENABLED, Grid.ICON_SIZE_PERCENT' in text:
    print('already registered')
elif text.count(old) != 1:
    raise SystemExit(f'expected exactly one Grid registration anchor, got {text.count(old)}')
else:
    path.write_text(text.replace(old, new, 1))
