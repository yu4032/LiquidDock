from pathlib import Path


def insert_before(path: str, marker: str, insertion: str, guard: str) -> None:
    target = Path(path)
    text = target.read_text()
    if guard in text:
        print(f"{path}: guard already present")
        return
    count = text.count(marker)
    print(f"{path}: anchor matches={count}: {marker!r}")
    if count != 1:
        raise SystemExit(f"{path}: expected one anchor, got {count}")
    target.write_text(text.replace(marker, insertion + marker, 1))


def replace_once(path: str, old: str, new: str, guard: str) -> None:
    target = Path(path)
    text = target.read_text()
    if guard in text:
        print(f"{path}: guard already present")
        return
    count = text.count(old)
    print(f"{path}: anchor matches={count}: {old!r}")
    if count != 1:
        raise SystemExit(f"{path}: expected one anchor, got {count}")
    target.write_text(text.replace(old, new, 1))


insert_before(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java",
    "        public static final ConfigKey<Boolean> MARGINS_DP = bool(\n",
    """        public static final ConfigKey<Boolean> ICON_SIZE_ENABLED = bool(
                \"launcher_icon_size_enabled\", false, false, false, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> ICON_SIZE_PERCENT = integer(
                \"launcher_icon_size_percent\", 100, 100, 100, 80, 120,
                ConfigKey.ExportMode.ALWAYS);
""",
    "launcher_icon_size_percent",
)

replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    "        final boolean enabled, widgetAdaptation, dp, offsets;\n",
    "        final boolean enabled, widgetAdaptation, iconSizeEnabled, dp, offsets;\n"
    "        final int iconSizePercent;\n",
    "final int iconSizePercent;",
)

insert_before(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    "            dp = c.b(ConfigSchema.Grid.MARGINS_DP.name(),\n",
    """            iconSizeEnabled = c.b(ConfigSchema.Grid.ICON_SIZE_ENABLED.name(),
                    ConfigSchema.Grid.ICON_SIZE_ENABLED.runtimeFallback());
            iconSizePercent = Math.max(LauncherIconSizePolicy.MIN_PERCENT,
                    Math.min(LauncherIconSizePolicy.MAX_PERCENT, c.i(
                            ConfigSchema.Grid.ICON_SIZE_PERCENT.name(),
                            ConfigSchema.Grid.ICON_SIZE_PERCENT.runtimeFallback())));
""",
    "iconSizeEnabled = c.b(ConfigSchema.Grid.ICON_SIZE_ENABLED.name()",
)

insert_before(
    "src/main/java/com/hellovoid/liquiddock/ModuleMain.java",
    "            new MainHook().install(classLoader);\n",
    """            LauncherIconSizeHook.install(classLoader,
                    runtimeConfig.enabled && runtimeConfig.grid.iconSizeEnabled,
                    runtimeConfig.grid.iconSizePercent);
""",
    "LauncherIconSizeHook.install(classLoader",
)
