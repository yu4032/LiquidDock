from pathlib import Path

p = Path('src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt')
text = p.read_text()
old = '''        item { SmallTitle(stringResource(R.string.category_grid)) }
        item {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Grid.ENABLED, stringResource(R.string.enable_grid_8x4), stringResource(R.string.enable_grid_8x4_summary), masterEnabled) { customGrid = it }
                StringDropdown(
                    prefs = prefs,
                    config = ConfigSchema.Grid.PROFILE,
                    title = stringResource(R.string.grid_profile_title),
                    options = profileOptions,
                    enabled = masterEnabled && customGrid,
                )
                BooleanSetting(prefs, ConfigSchema.Grid.WIDGET_ADAPTATION, stringResource(R.string.enable_widget_adaptation), stringResource(R.string.enable_widget_adaptation_summary), masterEnabled && customGrid)
            }
        }
        item { SmallTitle("图标大小 · Launcher 4.50") }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Grid.ICON_SIZE_ENABLED,
                    "自定义图标大小",
                    "仅作用于工作区、Dock 与小文件夹；重启桌面后生效",
                    masterEnabled,
                ) { launcher450IconSizeEnabled = it }
                IntSetting(
                    prefs,
                    launcher450IconSizeSpec,
                    masterEnabled && launcher450IconSizeEnabled,
                )
            }
        }
'''
new = '''        item { SmallTitle("图标大小 · Launcher 4.50") }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Grid.ICON_SIZE_ENABLED,
                    "自定义图标大小",
                    "作用于工作区、Dock、小文件夹、文件夹内图标与工作台 App 页；重启桌面后生效",
                    masterEnabled,
                ) { launcher450IconSizeEnabled = it }
                IntSetting(
                    prefs,
                    launcher450IconSizeSpec,
                    masterEnabled && launcher450IconSizeEnabled,
                )
            }
        }
        item { SmallTitle(stringResource(R.string.category_grid)) }
        item {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Grid.ENABLED, stringResource(R.string.enable_grid_8x4), stringResource(R.string.enable_grid_8x4_summary), masterEnabled) { customGrid = it }
                StringDropdown(
                    prefs = prefs,
                    config = ConfigSchema.Grid.PROFILE,
                    title = stringResource(R.string.grid_profile_title),
                    options = profileOptions,
                    enabled = masterEnabled && customGrid,
                )
                BooleanSetting(prefs, ConfigSchema.Grid.WIDGET_ADAPTATION, stringResource(R.string.enable_widget_adaptation), stringResource(R.string.enable_widget_adaptation_summary), masterEnabled && customGrid)
            }
        }
'''
if old not in text:
    raise SystemExit('GridPage icon-size/grid block not found exactly')
text = text.replace(old, new, 1)
p.write_text(text)
