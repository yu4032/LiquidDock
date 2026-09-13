from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f'anchor not found in {path}: {old[:120]!r}')
    p.write_text(text.replace(old, new, 1))

# Config schema: independent, default-off functional-only mode.
replace_once(
    'src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java',
    '''        public static final ConfigKey<Boolean> ICON_GLASS = bool(\n                "liquid_icon_glass", true, true, true, ConfigKey.ExportMode.ALWAYS);\n''',
    '''        public static final ConfigKey<Boolean> ICON_GLASS = bool(\n                "liquid_icon_glass", true, true, true, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Boolean> FUNCTIONAL_DOCK_ICON_GLASS = bool(\n                "liquid_functional_dock_icon_glass", false, false, false,\n                ConfigKey.ExportMode.ALWAYS);\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java',
    '''                Glass.WIDGET_DARK_CONTENT, Glass.ICON_GLASS,\n''',
    '''                Glass.WIDGET_DARK_CONTENT, Glass.ICON_GLASS,\n                Glass.FUNCTIONAL_DOCK_ICON_GLASS,\n''')

# Runtime config carries the two icon modes separately.
replace_once(
    'src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java',
    '''        final boolean enabled, securityCenterEnabled, folderEnabled, widgetEnabled,\n                widgetDarkContent, iconEnabled;\n''',
    '''        final boolean enabled, securityCenterEnabled, folderEnabled, widgetEnabled,\n                widgetDarkContent, iconEnabled, functionalDockIconEnabled;\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java',
    '''            boolean resolvedIconEnabled = c.b(ConfigSchema.Glass.ICON_GLASS.name(),\n                    ConfigSchema.Glass.ICON_GLASS.runtimeFallback());\n''',
    '''            boolean resolvedIconEnabled = c.b(ConfigSchema.Glass.ICON_GLASS.name(),\n                    ConfigSchema.Glass.ICON_GLASS.runtimeFallback());\n            functionalDockIconEnabled = c.b(\n                    ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.name(),\n                    ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.runtimeFallback());\n''')

# Runtime state keeps full-icon and functional-only modes distinct, while the existing transition
# policy sees their union as icon ownership being alive.
replace_once(
    'src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java',
    '''    private static volatile boolean iconEnabled;\n''',
    '''    private static volatile boolean iconEnabled;\n    private static volatile boolean functionalDockIconEnabled;\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java',
    '''            boolean initialIconEnabled,\n            boolean initialWidgetEnabled,\n''',
    '''            boolean initialIconEnabled,\n            boolean initialFunctionalDockIconEnabled,\n            boolean initialWidgetEnabled,\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java',
    '''        iconEnabled = initialIconEnabled;\n        widgetEnabled = initialWidgetEnabled;\n''',
    '''        iconEnabled = initialIconEnabled;\n        functionalDockIconEnabled = initialFunctionalDockIconEnabled;\n        widgetEnabled = initialWidgetEnabled;\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java',
    '''                    && !ConfigSchema.Glass.ICON_GLASS.name().equals(key)\n                    && !ConfigSchema.Glass.WIDGET_GLASS.name().equals(key)\n''',
    '''                    && !ConfigSchema.Glass.ICON_GLASS.name().equals(key)\n                    && !ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.name().equals(key)\n                    && !ConfigSchema.Glass.WIDGET_GLASS.name().equals(key)\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java',
    '''            boolean nextWidgetEnabled = sharedPreferences.getBoolean(\n                    ConfigSchema.Glass.WIDGET_GLASS.name(),\n''',
    '''            boolean nextFunctionalDockIconEnabled = sharedPreferences.getBoolean(\n                    ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.name(),\n                    ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.runtimeFallback());\n            boolean nextWidgetEnabled = sharedPreferences.getBoolean(\n                    ConfigSchema.Glass.WIDGET_GLASS.name(),\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java',
    '''            apply(nextEnabled, nextIconEnabled, nextWidgetEnabled, nextWidgetDarkContentEnabled,\n                    nextSmallFolderEnabled, nextLargeFolderEnabled);\n''',
    '''            apply(nextEnabled, nextIconEnabled, nextFunctionalDockIconEnabled,\n                    nextWidgetEnabled, nextWidgetDarkContentEnabled,\n                    nextSmallFolderEnabled, nextLargeFolderEnabled);\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java',
    '''                + " iconEnabled=" + isIconEnabled()\n                + " widgetEnabled=" + isWidgetEnabled()\n''',
    '''                + " iconEnabled=" + isIconEnabled()\n                + " functionalDockIconEnabled=" + isFunctionalDockIconEnabled()\n                + " widgetEnabled=" + isWidgetEnabled()\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java',
    '''    static boolean isIconEnabled() { return enabled && iconEnabled; }\n    static boolean isWidgetEnabled() { return enabled && widgetEnabled; }\n''',
    '''    static boolean isIconEnabled() { return enabled && iconEnabled; }\n    static boolean isFunctionalDockIconEnabled() {\n        return enabled && functionalDockIconEnabled;\n    }\n    static boolean isAnyIconEnabled() {\n        return isIconEnabled() || isFunctionalDockIconEnabled();\n    }\n    static boolean isWidgetEnabled() { return enabled && widgetEnabled; }\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java',
    '''                isEnabled(), isIconEnabled(), isWidgetEnabled(), isWidgetDarkContentEnabled(),\n''',
    '''                isEnabled(), isAnyIconEnabled(), isWidgetEnabled(), isWidgetDarkContentEnabled(),\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java',
    '''            boolean nextEnabled,\n            boolean nextIconEnabled,\n            boolean nextWidgetEnabled,\n''',
    '''            boolean nextEnabled,\n            boolean nextIconEnabled,\n            boolean nextFunctionalDockIconEnabled,\n            boolean nextWidgetEnabled,\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java',
    '''        if (enabled == nextEnabled\n                && iconEnabled == nextIconEnabled\n                && widgetEnabled == nextWidgetEnabled\n''',
    '''        if (enabled == nextEnabled\n                && iconEnabled == nextIconEnabled\n                && functionalDockIconEnabled == nextFunctionalDockIconEnabled\n                && widgetEnabled == nextWidgetEnabled\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java',
    '''        GlassRuntimeTransitionPolicy.Snapshot before = snapshot();\n\n        enabled = nextEnabled;\n        iconEnabled = nextIconEnabled;\n''',
    '''        GlassRuntimeTransitionPolicy.Snapshot before = snapshot();\n        boolean iconPolicyChanged = iconEnabled != nextIconEnabled\n                || functionalDockIconEnabled != nextFunctionalDockIconEnabled;\n\n        enabled = nextEnabled;\n        iconEnabled = nextIconEnabled;\n        functionalDockIconEnabled = nextFunctionalDockIconEnabled;\n''')
# Second identical log occurrence.
replace_once(
    'src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java',
    '''                + " iconEnabled=" + isIconEnabled()\n                + " widgetEnabled=" + isWidgetEnabled()\n''',
    '''                + " iconEnabled=" + isIconEnabled()\n                + " functionalDockIconEnabled=" + isFunctionalDockIconEnabled()\n                + " widgetEnabled=" + isWidgetEnabled()\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java',
    '''        if (transition.iconRelease) {\n            runOnMain(() -> {\n                MiuixLauncherStaticGlassHook.onRuntimeIconGlassDisabled();\n                MiuixLauncherDragOverlayHook.onRuntimeIconGlassDisabled();\n                MainHook.log("[DC][GlassRuntime] icon glass ownership released");\n            });\n        }\n''',
    '''        if (transition.iconRelease) {\n            runOnMain(() -> {\n                MiuixLauncherStaticGlassHook.onRuntimeIconGlassDisabled();\n                MiuixLauncherDragOverlayHook.onRuntimeIconGlassDisabled();\n                MainHook.log("[DC][GlassRuntime] icon glass ownership released");\n            });\n        } else if (iconPolicyChanged) {\n            runOnMain(() -> {\n                MiuixLauncherStaticGlassHook.onRuntimeIconGlassPolicyChanged();\n                // A mode switch must not leave a normal-app drag overlay alive from the old mode.\n                MiuixLauncherDragOverlayHook.onRuntimeIconGlassDisabled();\n                MainHook.log("[DC][GlassRuntime] icon glass policy reconciled");\n            });\n        }\n''')

# Composition root: pass the extra state and install the vendor adapter authority.
replace_once(
    'src/main/java/com/hellovoid/liquiddock/ModuleMain.java',
    '''                    runtimeConfig.glass.iconEnabled,\n                    runtimeConfig.glass.widgetEnabled,\n''',
    '''                    runtimeConfig.glass.iconEnabled,\n                    runtimeConfig.glass.functionalDockIconEnabled,\n                    runtimeConfig.glass.widgetEnabled,\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/ModuleMain.java',
    '''            Launcher450IconSizeHook.install(classLoader,\n                    runtimeConfig.enabled && runtimeConfig.grid.iconSizeEnabled,\n                    runtimeConfig.grid.iconSizePercent);\n            new MainHook().install(classLoader);\n''',
    '''            Launcher450IconSizeHook.install(classLoader,\n                    runtimeConfig.enabled && runtimeConfig.grid.iconSizeEnabled,\n                    runtimeConfig.grid.iconSizePercent);\n            Launcher450DockFunctionalIconRegistry.install(classLoader);\n            new MainHook().install(classLoader);\n''')

# Static glass ownership: full-icon mode remains unchanged; functional-only mode is Dock +
# vendor-marked functional item only. RecyclerView rebinding re-enters this same gate.
replace_once(
    'src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java',
    '''    private static boolean installed;\n''',
    '''    private static boolean installed;\n    private static LiquidDockConfig.Glass installedGlassConfig;\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java',
    '''        LiquidDockConfig.Glass glassConfig = runtimeConfig.glass;\n        boolean any = false;\n''',
    '''        LiquidDockConfig.Glass glassConfig = runtimeConfig.glass;\n        installedGlassConfig = glassConfig;\n        boolean any = false;\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java',
    '''    static void onRuntimeIconGlassDisabled() {\n        for (View host : new ArrayList<>(BOOTSTRAP_OBSERVERS.keySet())) {\n            if (!isIconHost(host)) continue;\n            DockGlassItemRegistry.unregister(host);\n            LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);\n            if (node != null && node.kind() == LauncherGlassDragState.Kind.ICON) node.dispose();\n        }\n    }\n''',
    '''    static void onRuntimeIconGlassDisabled() {\n        for (View host : new ArrayList<>(BOOTSTRAP_OBSERVERS.keySet())) {\n            if (!isIconHost(host)) continue;\n            releaseIconHost(host);\n        }\n    }\n\n    static void onRuntimeIconGlassPolicyChanged() {\n        for (View host : new ArrayList<>(BOOTSTRAP_OBSERVERS.keySet())) {\n            if (!isIconHost(host)) continue;\n            if (!isIconHostEligible(host)) {\n                releaseIconHost(host);\n            } else if (installedGlassConfig != null && host.isAttachedToWindow()) {\n                scheduleBind(host, LauncherGlassDragState.Kind.ICON, installedGlassConfig, 0);\n            }\n        }\n    }\n\n    static void onDockIconAdapterBound(View host) {\n        if (host == null || installedGlassConfig == null || !isIconHost(host)) return;\n        observeHost(host, LauncherGlassDragState.Kind.ICON, installedGlassConfig);\n    }\n\n    private static void releaseIconHost(View host) {\n        DockGlassItemRegistry.unregister(host);\n        LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);\n        if (node != null && node.kind() == LauncherGlassDragState.Kind.ICON) node.dispose();\n    }\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java',
    '''        if (GlassRuntimeState.isIconEnabled() && (name.endsWith(".ShortcutIcon")\n                || "ShortcutIcon".equals(host.getClass().getSimpleName()))) {\n''',
    '''        if (GlassRuntimeState.isAnyIconEnabled() && isIconHost(host)) {\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java',
    '''        if (kind == LauncherGlassDragState.Kind.ICON && !GlassRuntimeState.isIconEnabled()) {\n            DockGlassItemRegistry.unregister(host);\n            LauncherGlassStaticNode staleNode = LauncherGlassStaticNode.find(host);\n            if (staleNode != null && staleNode.kind() == LauncherGlassDragState.Kind.ICON) {\n                staleNode.dispose();\n            }\n            return;\n        }\n''',
    '''        if (kind == LauncherGlassDragState.Kind.ICON && !isIconHostEligible(host)) {\n            releaseIconHost(host);\n            return;\n        }\n''')
replace_once(
    'src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java',
    '''    private static boolean isIconHost(View host) {\n        if (host == null) return false;\n        String name = host.getClass().getName();\n        return name.endsWith(".ShortcutIcon")\n                || "ShortcutIcon".equals(host.getClass().getSimpleName());\n    }\n''',
    '''    private static boolean isIconHostEligible(View host) {\n        if (host == null || !GlassRuntimeState.isAnyIconEnabled()) return false;\n        if (GlassRuntimeState.isIconEnabled()) return true;\n        return GlassRuntimeState.isFunctionalDockIconEnabled()\n                && LauncherGlassHierarchy.classify(host) == LauncherGlassHierarchy.Domain.DOCK\n                && Launcher450DockFunctionalIconRegistry.isFunctional(host);\n    }\n\n    private static boolean isIconHost(View host) {\n        if (host == null) return false;\n        Class<?> current = host.getClass();\n        while (current != null) {\n            if ("com.miui.home.launcher.ShortcutIcon".equals(current.getName())) return true;\n            current = current.getSuperclass();\n        }\n        return false;\n    }\n''')

# Settings: functional-only mode is independent from the broad icon switch and reuses the same
# size/radius styling controls when either icon mode is active.
replace_once(
    'src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt',
    '''    var iconGlass by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.ICON_GLASS.name(), ConfigSchema.Glass.ICON_GLASS.uiDefault())) }\n    var widgetGlass by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.WIDGET_GLASS.name(), ConfigSchema.Glass.WIDGET_GLASS.uiDefault())) }\n''',
    '''    var iconGlass by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.ICON_GLASS.name(), ConfigSchema.Glass.ICON_GLASS.uiDefault())) }\n    var functionalDockIconGlass by remember { mutableStateOf(prefs.getBoolean(\n        ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.name(),\n        ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS.uiDefault(),\n    )) }\n    var widgetGlass by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.WIDGET_GLASS.name(), ConfigSchema.Glass.WIDGET_GLASS.uiDefault())) }\n''')
replace_once(
    'src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt',
    '''        BooleanSetting(prefs, ConfigSchema.Glass.ICON_GLASS, "图标玻璃", "同时控制桌面与 Dock 图标；0 圆角为 Auto", masterEnabled && liquidGlass) { iconGlass = it }\n        IntSetting(prefs, iconSizeOffsetSpec, masterEnabled && liquidGlass && iconGlass)\n        IntSetting(prefs, iconCornerRadiusSpec, masterEnabled && liquidGlass && iconGlass)\n''',
    '''        BooleanSetting(prefs, ConfigSchema.Glass.ICON_GLASS, "图标玻璃", "同时控制桌面与 Dock 全部图标；0 圆角为 Auto", masterEnabled && liquidGlass) { iconGlass = it }\n        BooleanSetting(\n            prefs,\n            ConfigSchema.Glass.FUNCTIONAL_DOCK_ICON_GLASS,\n            "仅 Dock 功能图标玻璃",\n            "仅搜索、小爱、全部应用、最近任务、Home、手机互联等系统功能入口；可在关闭“图标玻璃”后单独使用",\n            masterEnabled && liquidGlass,\n        ) { functionalDockIconGlass = it }\n        IntSetting(prefs, iconSizeOffsetSpec, masterEnabled && liquidGlass && (iconGlass || functionalDockIconGlass))\n        IntSetting(prefs, iconCornerRadiusSpec, masterEnabled && liquidGlass && (iconGlass || functionalDockIconGlass))\n''')
