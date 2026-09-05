from pathlib import Path

ROOT = Path('src/main/java/com/hellovoid/liquiddock')


def replace_exact(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{path.name} {label}: expected exactly one match, got {count}')
    path.write_text(text.replace(old, new, 1))


vendor = ROOT / 'LauncherGlassVendorMaterialSuppressor.java'
replace_exact(
    vendor,
    '    private static final Map<View, Drawable> ORIGINAL_WIDGET_BACKGROUNDS =\n'
    '            Collections.synchronizedMap(new WeakHashMap<>());',
    '    private static final Map<View, OwnedValueState<Drawable>> WIDGET_BACKGROUND_OWNERSHIP =\n'
    '            Collections.synchronizedMap(new WeakHashMap<>());',
    'widget ownership map')
replace_exact(
    vendor,
    '        View remoteViewsContent = resolveRemoteViewsContent(host);\n'
    '        if (remoteViewsContent != null) {\n'
    '            Drawable current = remoteViewsContent.getBackground();\n'
    '            if (current != null) ORIGINAL_WIDGET_BACKGROUNDS.put(remoteViewsContent, current);\n'
    '            remoteViewsContent.setBackground(null);\n'
    '        }',
    '        View remoteViewsContent = resolveRemoteViewsContent(host);\n'
    '        if (remoteViewsContent != null) {\n'
    '            Drawable current = remoteViewsContent.getBackground();\n'
    '            if (current != null) {\n'
    '                OwnedValueState<Drawable> ownership = WIDGET_BACKGROUND_OWNERSHIP\n'
    '                        .computeIfAbsent(remoteViewsContent, ignored -> new OwnedValueState<>());\n'
    '                ownership.claim(current);\n'
    '            }\n'
    '            remoteViewsContent.setBackground(null);\n'
    '        }',
    'widget claim')
replace_exact(
    vendor,
    '        View remoteViewsContent = resolveRemoteViewsContent(host);\n'
    '        if (remoteViewsContent != null) {\n'
    '            Drawable original = ORIGINAL_WIDGET_BACKGROUNDS.remove(remoteViewsContent);\n'
    '            if (original != null && remoteViewsContent.getBackground() == null) {\n'
    '                remoteViewsContent.setBackground(original);\n'
    '            }\n'
    '        }',
    '        View remoteViewsContent = resolveRemoteViewsContent(host);\n'
    '        if (remoteViewsContent != null) {\n'
    '            OwnedValueState<Drawable> ownership =\n'
    '                    WIDGET_BACKGROUND_OWNERSHIP.remove(remoteViewsContent);\n'
    '            if (ownership != null) {\n'
    '                OwnedValueState.ReleaseDecision<Drawable> release =\n'
    '                        ownership.release(remoteViewsContent.getBackground() == null);\n'
    '                if (release.restoreOriginal && release.originalValue != null) {\n'
    '                    remoteViewsContent.setBackground(release.originalValue);\n'
    '                }\n'
    '            }\n'
    '        }',
    'widget release')

folder = ROOT / 'MiuixFolderGlassHook.java'
replace_exact(
    folder,
    '    private static final Map<View, Integer> ORIGINAL_IMAGE_ALPHA =\n'
    '            Collections.synchronizedMap(new WeakHashMap<>());\n'
    '    private static final Map<View, Drawable> ORIGINAL_BACKGROUND =\n'
    '            Collections.synchronizedMap(new WeakHashMap<>());',
    '    private static final Map<View, OwnedValueState<Integer>> IMAGE_ALPHA_OWNERSHIP =\n'
    '            Collections.synchronizedMap(new WeakHashMap<>());\n'
    '    private static final Map<View, OwnedValueState<Drawable>> BACKGROUND_OWNERSHIP =\n'
    '            Collections.synchronizedMap(new WeakHashMap<>());',
    'folder ownership maps')
replace_exact(
    folder,
    '        if (material instanceof ImageView) {\n'
    '            ImageView image = (ImageView) material;\n'
    '            if (!ORIGINAL_IMAGE_ALPHA.containsKey(material)) {\n'
    '                ORIGINAL_IMAGE_ALPHA.put(material, image.getImageAlpha());\n'
    '            }\n'
    '            image.setImageAlpha(0);\n'
    '        } else {\n'
    '            Drawable current = material.getBackground();\n'
    '            if (!ORIGINAL_BACKGROUND.containsKey(material) && current != null\n'
    '                    && !isTransparentColorDrawable(current)) {\n'
    '                ORIGINAL_BACKGROUND.put(material, current);\n'
    '            }\n'
    '            material.setBackground(new ColorDrawable(Color.TRANSPARENT));\n'
    '        }',
    '        if (material instanceof ImageView) {\n'
    '            ImageView image = (ImageView) material;\n'
    '            OwnedValueState<Integer> ownership = IMAGE_ALPHA_OWNERSHIP\n'
    '                    .computeIfAbsent(material, ignored -> new OwnedValueState<>());\n'
    '            ownership.claim(image.getImageAlpha());\n'
    '            image.setImageAlpha(0);\n'
    '        } else {\n'
    '            Drawable current = material.getBackground();\n'
    '            if (current != null && !isTransparentColorDrawable(current)) {\n'
    '                OwnedValueState<Drawable> ownership = BACKGROUND_OWNERSHIP\n'
    '                        .computeIfAbsent(material, ignored -> new OwnedValueState<>());\n'
    '                ownership.claim(current);\n'
    '            }\n'
    '            material.setBackground(new ColorDrawable(Color.TRANSPARENT));\n'
    '        }',
    'folder claim')
replace_exact(
    folder,
    '        if (material instanceof ImageView) {\n'
    '            Integer originalAlpha = ORIGINAL_IMAGE_ALPHA.remove(material);\n'
    '            if (originalAlpha != null) ((ImageView) material).setImageAlpha(originalAlpha);\n'
    '        } else {\n'
    '            Drawable original = ORIGINAL_BACKGROUND.remove(material);\n'
    '            if (original != null) material.setBackground(original);\n'
    '        }',
    '        if (material instanceof ImageView) {\n'
    '            ImageView image = (ImageView) material;\n'
    '            OwnedValueState<Integer> ownership = IMAGE_ALPHA_OWNERSHIP.remove(material);\n'
    '            if (ownership != null) {\n'
    '                OwnedValueState.ReleaseDecision<Integer> release =\n'
    '                        ownership.release(image.getImageAlpha() == 0);\n'
    '                if (release.restoreOriginal && release.originalValue != null) {\n'
    '                    image.setImageAlpha(release.originalValue);\n'
    '                }\n'
    '            }\n'
    '        } else {\n'
    '            OwnedValueState<Drawable> ownership = BACKGROUND_OWNERSHIP.remove(material);\n'
    '            if (ownership != null) {\n'
    '                OwnedValueState.ReleaseDecision<Drawable> release =\n'
    '                        ownership.release(isTransparentColorDrawable(material.getBackground()));\n'
    '                if (release.restoreOriginal && release.originalValue != null) {\n'
    '                    material.setBackground(release.originalValue);\n'
    '                }\n'
    '            }\n'
    '        }',
    'folder release')

for path, forbidden in (
        (vendor, ('ORIGINAL_WIDGET_BACKGROUNDS',)),
        (folder, ('ORIGINAL_IMAGE_ALPHA', 'ORIGINAL_BACKGROUND'))):
    text = path.read_text()
    for token in forbidden:
        if token in text:
            raise SystemExit(f'{path.name}: raw ownership token remains: {token}')
    if 'OwnedValueState' not in text:
        raise SystemExit(f'{path.name}: OwnedValueState wiring missing')
