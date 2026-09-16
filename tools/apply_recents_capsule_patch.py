from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"anchor missing: {path}")
    p.write_text(text.replace(old, new, 1))


replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java",
    '''        public static final ConfigKey<Boolean> FUNCTIONAL_DOCK_ICON_GLASS = bool(
                "liquid_functional_dock_icon_glass", false, false, false,
                ConfigKey.ExportMode.ALWAYS);''',
    '''        public static final ConfigKey<Boolean> FUNCTIONAL_DOCK_ICON_GLASS = bool(
                "liquid_functional_dock_icon_glass", false, false, false,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Boolean> RECENTS_CAPSULE_GLASS = bool(
                "liquid_recents_capsule_glass", true, true, true,
                ConfigKey.ExportMode.ALWAYS);''',
)

replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    '''        final boolean enabled, securityCenterEnabled, folderEnabled, widgetEnabled,
                widgetDarkContent, iconEnabled, functionalDockIconEnabled;''',
    '''        final boolean enabled, securityCenterEnabled, folderEnabled, widgetEnabled,
                widgetDarkContent, iconEnabled, functionalDockIconEnabled, recentsCapsuleEnabled;''',
)

replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    '''            functionalDockIconEnabled = c.b(
                    ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.name(),
                    ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.runtimeFallback());''',
    '''            functionalDockIconEnabled = c.b(
                    ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.name(),
                    ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.runtimeFallback());
            recentsCapsuleEnabled = c.b(
                    ConfigSchema.Glass.RECENTS_CAPSULE_GLASS.name(),
                    ConfigSchema.Glass.RECENTS_CAPSULE_GLASS.runtimeFallback());''',
)

replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    '''        ) { functionalDockIconGlass = it }
        IntSetting(prefs, iconSizeOffsetSpec, masterEnabled && liquidGlass && (iconGlass || functionalDockIconGlass))''',
    '''        ) { functionalDockIconGlass = it }
        BooleanSetting(
            prefs,
            ConfigSchema.Glass.RECENTS_CAPSULE_GLASS,
            "多任务操作按钮玻璃",
            "将多任务界面的清除全部和设备互联胶囊背景替换为完整 Prismal 液态玻璃",
            masterEnabled && liquidGlass,
        )
        IntSetting(prefs, iconSizeOffsetSpec, masterEnabled && liquidGlass && (iconGlass || functionalDockIconGlass))''',
)
