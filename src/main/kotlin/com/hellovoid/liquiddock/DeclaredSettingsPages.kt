package com.hellovoid.liquiddock

import com.hellovoid.liquiddock.config.ConfigKey
import com.hellovoid.liquiddock.config.ConfigSchema

/** These declarations are consumed both by the page renderer and search. */
/**
 * OS4 原生玻璃字段按柔光玻璃模型分节：材质决定光怎么进出，色彩决定显色与阴影，
 * 光影决定光的来源与边缘；采样页不承载原生字段。
 */
internal val liquidNativeSections: Map<Page, List<Pair<String, List<String>>>> = mapOf(
    Page.LiquidMaterial to listOf(
        "模糊与厚度" to listOf("blur_background", "blur_refract", "thickness_px"),
        "折射与轮廓" to listOf("ior", "edge_px", "edge_pow"),
    ),
    Page.LiquidColor to listOf(
        "颜色与透明度" to listOf("glass_r", "glass_g", "glass_b", "glass_a", "alpha"),
        "色调与饱和度" to listOf("unshade", "color_pow", "saturation", "brightness",
            "bg_saturation", "bg_brightness"),
        "暗部与亮度曲线" to listOf("darker", "darker_low", "darker_high", "luminance_amount",
            "luminance_c0", "luminance_c1", "luminance_c2", "luminance_c3"),
        "内层阴影" to listOf("inner_white", "inner_mix", "burn"),
    ),
    Page.LiquidLighting to listOf(
        "光源" to listOf("light_x", "light_y", "light_z", "light_intensity", "light_opposite",
            "light_range", "light_edge_pow"),
        "反射与内光" to listOf("reflection_strength", "reflection_lighten", "inner_bottom",
            "reflect_offset_px"),
    ),
    Page.LiquidSampling to emptyList(),
)

internal fun declaredSettingsPages(text: (Int) -> String): Map<Page, SettingsMeta> = buildMap {
    val glass = listOf(ConfigSchema.Glass.ENABLED to true)
    val dock = listOf(ConfigSchema.Dock.ENABLED to true)
    val grid = listOf(ConfigSchema.Grid.ENABLED to true)
    val workstation = listOf(ConfigSchema.Workstation.DOCK_CUSTOMIZATION to true)
    fun row(spec: IntSpec, requires: List<Pair<ConfigKey<Boolean>, Boolean>> = emptyList()) =
        SettingsBlock.Row(spec.key, spec.key, spec.title, spec.summary, number = spec, requires = requires)
    fun toggle(config: ConfigKey<Boolean>, title: String, summary: String = "",
               requires: List<Pair<ConfigKey<Boolean>, Boolean>> = emptyList()) =
        SettingsBlock.Row(config.name(), config.name(), title, summary, toggle = config, requires = requires)
    fun page(page: Page, summary: String, blocks: List<SettingsBlock>,
             requires: List<Pair<ConfigKey<Boolean>, Boolean>> = emptyList()) {
        put(page, SettingsMeta(page, text(page.titleRes),
            listOf(SettingsBlock.Info("page.summary", summary)) + blocks, requires))
    }
    fun numbers(page: Page, summary: String, specs: List<IntSpec>,
                requires: List<Pair<ConfigKey<Boolean>, Boolean>> = emptyList()) =
        page(page, summary, specs.map { row(it) }, requires)
    fun groups(groups: List<LiquidSpecGroup>): List<SettingsBlock> = groups.flatMapIndexed { i, group ->
        listOf(SettingsBlock.Header("group.$i", group.title)) + group.specs.map { row(it) }
    }
    page(Page.LiquidMaterial, "玻璃本体与几何：模糊、厚度、透射率、折射率、法线位移与边缘轮廓。",
        groups(liquidSoftLightMaterialGroups), glass)
    page(Page.LiquidColor, "显色与阴影：亮度、染色、底色透明度、色散、饱和度、色调曲线与内阴影。",
        groups(liquidSoftLightColorGroups), glass)
    page(Page.LiquidLighting, "光的来源与边缘：高光、焦散、菲涅尔反射、光源方向、OS4 反射、外阴影与描边。",
        groups(liquidSoftLightLightingGroups) + listOf(
            SettingsBlock.Header("lighting.edge", "外阴影与描边"),
            SettingsBlock.Link("lighting.edge.scope", text(R.string.page_glass_edge),
                "Prismal 外阴影与光学描边，按作用域（Dock、图标、小组件…）独立调整", Page.GlassEdge),
        ), glass)
    page(Page.LiquidSampling, "渲染开销：背景缩放、视差、实时采样帧率与调试显示。",
        groups(liquidSoftLightSamplingGroups) + listOf(
            SettingsBlock.Header("sampling.performance", "实时背景采样与帧同步"),
            row(passBlurCaptureScaleSpec), row(passBlurRenderFpsSpec),
            toggle(ConfigSchema.Dock.FRAME_SYNC, text(R.string.dock_frame_sync), text(R.string.dock_frame_sync_summary)),
            SettingsBlock.Header("sampling.debug", "调试显示"),
            toggle(ConfigSchema.Glass.PRISMAL_SHOW_NORMALS, "OS4 法线与方向光可视化",
                "内部显示玻璃表面法线，边缘显示 OS4 反射使用的 SDF 法线；亮度反映 OS4 方向光，不包含背景反射纹理"),
        ), glass)
    numbers(Page.DockGeometry, "调整 Dock 的尺寸、位置、圆角与图标间距。", dockSpecs, dock)
    numbers(Page.GridSplit, "分屏使用系统 pane 作为边界，只在这里调整额外距离。", splitGridSpecs, grid)
    listOf(Page.GridLandscape to "landscape", Page.GridPortrait to "portrait").forEach { (p, orientation) ->
        val specs = gridSpecs.filter { it.key.startsWith("grid_$orientation") || it.key == "indicator_${orientation}_y" }
        page(p, "调整${if (orientation == "landscape") "横" else "竖"}屏桌面的间距与页面指示器位置。", buildList {
            add(SettingsBlock.Header("grid.position", "工作区位置"))
            addAll(specs.filterNot { it.key.endsWith("row_gap") || it.key.startsWith("indicator_") }.map { row(it) })
            add(SettingsBlock.Header("grid.gap", "图标行间距"))
            addAll(specs.filter { it.key.endsWith("row_gap") }.map { row(it) })
            add(SettingsBlock.Header("grid.indicator", "页面指示器"))
            addAll(specs.filter { it.key.startsWith("indicator_") }.map { row(it) })
        }, grid)
    }
    page(Page.WorkstationDock, "调整工作台 Dock 的外观与图标位置。", listOf(
        toggle(ConfigSchema.Workstation.TASKBAR_MODE, "任务栏模式",
            "保留工作台 Dock 的原生位置及飞入、飞出动画，将液态玻璃或原生背景扩展为任务栏样式，不再自动下移。可使用整体垂直平移移动背景和图标，或分别设置横竖屏图标垂直偏移。"),
    ) + workstationDockSpecs.map { row(it) }, workstation)
    numbers(Page.WorkstationAppsLandscape, "所有应用横屏布局参数", workstationAppsLandscapeSpecs, workstation)
    numbers(Page.WorkstationAppsPortrait, "所有应用竖屏布局参数", workstationAppsPortraitSpecs, workstation)
    page(Page.DockBehavior, "管理 Dock 功能入口与尺寸变化动画。", listOf(
        toggle(ConfigSchema.Dock.HIDE_RECENT_TASK_ICONS, "隐藏 Dock 最近任务图标",
            "直接过滤 Dock 推荐的最近任务，不影响固定图标与系统多任务界面；重启桌面生效"),
        toggle(ConfigSchema.Dock.HIDE_MIRROR_SHORTCUT, "隐藏手机互联图标",
            "仅隐藏 Dock 入口，不修改系统互联开关或设备连接状态"),
        toggle(ConfigSchema.Dock.RESIZE_ANIMATION, text(R.string.dock_resize_animation), text(R.string.dock_resize_animation_summary), dock),
        toggle(ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION, text(R.string.dock_smooth_resize_animation),
            text(R.string.dock_smooth_resize_animation_summary), dock + (ConfigSchema.Dock.RESIZE_ANIMATION to false)),
    ))
    page(Page.Recents, text(R.string.recents_header_summary), listOf(
        row(recentsBlurSpec),
        toggle(ConfigSchema.Animation.DISABLE_WALLPAPER_ZOOM, "禁用壁纸缩放",
            "阻止桌面/多任务切换时系统壁纸的缩放目标，保留壁纸平移及其他过渡；首次安装该功能需重启桌面，之后切换即时生效"),
        toggle(ConfigSchema.Recents.DISABLE_WALLPAPER_DIMMING, "取消壁纸压暗",
            "取消多任务壁纸与转场的额外压暗，保留原生模糊、缩放和动画；下次切换起生效，无需重启桌面"),
    ))
    page(Page.AnimationWorkspace, "按工作区、应用返回和文件夹分别调整玻璃过渡时间。", listOf(
        SettingsBlock.Header("animation.workspace", "应用返回、工作区与文件夹"),
        toggle(ConfigSchema.Animation.HIDE_WORKSPACE_GLASS_DURING_APP, "应用动画时隐藏工作区玻璃",
            "只隐藏其他工作区图标、小组件和文件夹玻璃；正在启动或返回的图标始终跟随原生代理"),
        row(animationAppReturnGlassFadeStartSpec), row(animationAppReturnGlassFadeDurationSpec),
        row(animationWorkspaceVisibilitySpec),
        row(animationUnlockGlassFadeSpec),
        SettingsBlock.Header("animation.dock", "Dock 与图标"),
        toggle(ConfigSchema.Animation.HIDE_DOCK_ICONS_DURING_APP, "应用动画时隐藏其他 Dock 图标玻璃",
            "保留 Dock 背景和动画目标图标玻璃；目标图标始终跟随原生代理显隐"),
        row(animationDockResizeSpec, dock + listOf(ConfigSchema.Dock.RESIZE_ANIMATION to false, ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION to true)),
    ))
    numbers(Page.AnimationInteraction, "玻璃按下与释放反馈独立调节；0 ms 表示立即完成。", listOf(animationPressInSpec, animationPressOutSpec))
    numbers(Page.AnimationPopups, "调整快捷菜单与安全中心玻璃的退出渐隐。", listOf(animationShortcutDismissSpec, animationSecurityCenterExitSpec))
    numbers(Page.AnimationGui, "调整设置页面的切换动画。", listOf(animationSettingsPageSpec))
    page(Page.AnimationSystem, "控制通知与控制中心顶部模糊及系统界面联动。", listOf(
        toggle(ConfigSchema.Animation.SYSTEM_UI_HEADER_GRADIENT_BLUR,
            "控制中心/通知中心顶部渐变模糊",
            "只影响面板顶部渐变模糊，不改变面板主体背景模糊；需启用 LiquidDock 的 SystemUI 作用域，切换后重启系统界面生效"),
        toggle(ConfigSchema.Animation.HIDE_GESTURE_HANDLE_HOME_RECENTS, "桌面/多任务隐藏手势小白条",
            "进入桌面或多任务后隐藏手势手柄，离开后交还系统原生显示时序；开关即时生效，首次安装此版本需重启系统界面"),
    ))
    page(Page.GlassMenus, "菜单与弹窗按组件设置，系统级壁纸修复独立放置。", listOf(
        SettingsBlock.Header("menus.heading", "菜单与弹窗"),
        SettingsBlock.Link("menus.handle", text(R.string.page_handle_menu), "应用顶部控制器的菜单玻璃、颜色、模糊与独立光学参数", Page.HandleMenuCustomization),
        SettingsBlock.Link("menus.shortcut", text(R.string.page_shortcut_popup), "长按快捷菜单的玻璃、深色模式、模糊、颜色及独立光学参数", Page.ShortcutPopupCustomization),
        SettingsBlock.Link("menus.dialog", text(R.string.page_dialog_customization), "卸载、移除与二次确认弹窗的玻璃、背景压暗、模糊和颜色", Page.DialogCustomization),
        SettingsBlock.Header("menus.compatibility", "系统兼容性"),
        toggle(ConfigSchema.Glass.WALLPAPER_FLICKER_FIX, "壁纸闪烁修复",
            "仅在壁纸闪烁时开启；需要为 LiquidDock 启用 System Framework（system）作用域并重启设备"),
    ), glass)
    glassEdgePageScopes.forEach { (p, scope) ->
        val label = glassEdgeScopeLabels.getValue(scope)
        val global = scope == GlassEdgeEffects.GLOBAL
        fun edgeNumber(config: ConfigKey<Int>, title: String, unit: String = ""): SettingsBlock.Row {
            val spec = IntSpec(config, title, unit)
            val key = if (global) config.name() else GlassEdgeEffects.key(scope, config)
            return SettingsBlock.Row(key, key, title, spec.summary, number = spec, scope = if (global) null else scope)
        }
        fun edgeToggle(config: ConfigKey<Boolean>, title: String, summary: String): SettingsBlock.Row {
            val key = if (global) config.name() else GlassEdgeEffects.key(scope, config)
            return SettingsBlock.Row(key, key, title, summary, toggle = config, scope = if (global) null else scope)
        }
        val blocks = buildList {
            add(SettingsBlock.Info("edge.intro", "$label · 设置液态玻璃的 Prismal 外阴影，以及沿玻璃轮廓的折射描边。仅在对应组件启用液态玻璃时生效。" +
                if (global) "各组件可继承全局参数或独立调整。" else "本页未单独设置的参数继承全局默认。"))
            add(SettingsBlock.Header("edge.backlight", "玻璃边缘光照"))
            add(edgeToggle(ConfigSchema.Glass.BACKLIT_ENABLED, "启用折射光照描边", "同一条描边随折射背景与 OS4 光照连续变色"))
            add(edgeNumber(ConfigSchema.Glass.BACKLIT_STRENGTH, "描边不透明度", "%"))
            add(edgeNumber(ConfigSchema.Glass.BACKLIT_WIDTH, "细线宽度", "dp"))
            add(edgeNumber(ConfigSchema.Glass.BACKLIT_EXTENT, "暗色区延伸范围", "%"))
            add(edgeNumber(ConfigSchema.Glass.BACKLIT_SOFTNESS, "明暗过渡柔和度", "%"))
            add(SettingsBlock.Header("edge.shadow", if (scope == GlassEdgeEffects.DOCK) "Dock 独立 Prismal 阴影" else "整块玻璃的柔和投影"))
            add(edgeToggle(ConfigSchema.Glass.OUTER_SHADOW_ENABLED, "启用整体外阴影", "与真实窄边线分开，以整个圆角玻璃轮廓产生柔和投影"))
            add(edgeNumber(ConfigSchema.Glass.OUTER_SHADOW_RADIUS, "阴影柔和半径", "dp"))
            add(edgeNumber(ConfigSchema.Glass.OUTER_SHADOW_OFFSET_Y, "基础垂直偏移", "dp"))
            add(edgeNumber(ConfigSchema.Glass.OUTER_SHADOW_LIGHT_FOLLOW, "方向光响应", "%"))
            add(edgeNumber(ConfigSchema.Glass.OUTER_SHADOW_ALPHA, "阴影不透明度"))
            add(edgeNumber(ConfigSchema.Glass.OUTER_SHADOW_RED, "阴影红"))
            add(edgeNumber(ConfigSchema.Glass.OUTER_SHADOW_GREEN, "阴影绿"))
            add(edgeNumber(ConfigSchema.Glass.OUTER_SHADOW_BLUE, "阴影蓝"))
            if (global) {
                add(SettingsBlock.Header("edge.scopes", "各组件的独立阴影与描边"))
                glassEdgePageScopes.filterKeys { it != Page.GlassEdge }.forEach { (destination, childScope) ->
                    add(SettingsBlock.Link("edge.scope.$childScope", glassEdgeScopeLabels.getValue(childScope),
                        "独立设置 Prismal 外阴影与边缘描边", destination))
                }
            } else add(SettingsBlock.ResetEdge("edge.restore", scope))
        }
        put(p, SettingsMeta(p, "$label · ${text(p.titleRes)}", blocks, glass))
    }
}
