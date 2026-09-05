from pathlib import Path

path = Path('src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java')
text = path.read_text()

old = '''        } else {
            Drawable current = material.getBackground();
            if (current != null && !isTransparentColorDrawable(current)) {
                OwnedValueState<Drawable> ownership = BACKGROUND_OWNERSHIP
                        .computeIfAbsent(material, ignored -> new OwnedValueState<>());
                ownership.claim(current);
            }
            material.setBackground(new ColorDrawable(Color.TRANSPARENT));
        }'''
new = '''        } else {
            Drawable current = material.getBackground();
            OwnedValueState<Drawable> ownership = BACKGROUND_OWNERSHIP
                    .computeIfAbsent(material, ignored -> new OwnedValueState<>());
            ownership.claim(current);
            material.setBackground(new ColorDrawable(Color.TRANSPARENT));
        }'''
if text.count(old) != 1:
    raise SystemExit(f'background claim block count={text.count(old)}')
text = text.replace(old, new, 1)

old = '''                if (release.restoreOriginal && release.originalValue != null) {
                    material.setBackground(release.originalValue);
                }'''
new = '''                if (release.restoreOriginal) {
                    material.setBackground(release.originalValue);
                }'''
if text.count(old) != 1:
    raise SystemExit(f'background release block count={text.count(old)}')
text = text.replace(old, new, 1)

if 'current != null && !isTransparentColorDrawable(current)' in text:
    raise SystemExit('null background still excluded from ownership')
path.write_text(text)
