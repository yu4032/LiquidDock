package com.hellovoid.liquiddock

import com.hellovoid.liquiddock.config.ConfigKey
import com.hellovoid.liquiddock.config.ConfigSchema

/** These declarations are consumed both by the page renderer and search. */
/**
 * OS4 原生玻璃字段按柔光玻璃模型分节：材质决定光怎么进出，色彩决定显色与阴影，
 * 光影决定光的来源与边缘；采样页不承载原生字段。
 */
internal val liquidNativeSections: Map<Page, List<Pair<String, List<String>>>> = mapOf(
    Page.SoftGlassMaterial to listOf(
        "模糊与厚度" to listOf("blur_background", "blur_refract", "thickness_px"),
        "折射与轮廓" to listOf("ior", "edge_px", "edge_pow"),
    ),
    Page.SoftGlassColor to listOf(
        "颜色与透明度" to listOf("glass_r", "glass_g", "glass_b", "glass_a", "alpha"),
        "色调与饱和度" to listOf("unshade", "color_pow", "saturation", "brightness",
            "bg_saturation", "bg_brightness"),
        "暗部与亮度曲线" to listOf("darker", "darker_low", "darker_high", "luminance_amount",
            "luminance_c0", "luminance_c1", "luminance_c2", "luminance_c3"),
        "内层阴影" to listOf("inner_white", "inner_mix", "burn"),
    ),
    Page.SoftGlassLighting to listOf(
        "光源" to listOf("light_x", "light_y", "light_z", "light_intensity", "light_opposite",
            "light_range", "light_edge_pow"),
        "反射与内光" to listOf("reflection_strength", "reflection_lighten", "inner_bottom",
            "reflect_offset_px"),
    ),
)

internal fun declaredSettingsPages(text: (Int) -> String): Map<Page, SettingsMeta> = buildMap {
    val glass = listOf(ConfigSchema.Glass.ENABLED to true)
    val softGlass = listOf(ConfigSchema.Glass.SOFT_GLASS_ENABLED to true)
    val dock = listOf(ConfigSchema.Dock.ENABLED to true)
    val grid = listOf(ConfigSchema.Grid.ENABLED to true)
    val workstation = listOf(ConfigSchema.Workstation.DOCK_CUSTOMIZATION to true)
    fun row(spec: IntSpec, requires: List<Pair<ConfigKey<Boolean>, Boolean>> = emptyList()) =
        SettingsBlock.Row(spec.key, spec.key, spec.title, spec.summary, number = spec, requires = requires)
    fun toggle(config: ConfigKey<Boolean>, title: String, summary: String = "",
               requires: List<Pair<ConfigKey<Boolean>, Boolean>> = emptyList(),
               os4Unavailable: Boolean = false) =
        SettingsBlock.Row(config.name(), config.name(), title, summary, toggle = config,
            requires = requires, os4Unavailable = os4Unavailable)
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
    page(Page.SoftGlassMaterial, "系统桌面原生柔光玻璃的折射与轮廓参数。", emptyList(), softGlass)
    page(Page.SoftGlassColor, "系统桌面原生柔光玻璃的颜色、背景模糊与色调曲线。", emptyList(), softGlass)
    page(Page.SoftGlassLighting, "系统桌面原生柔光玻璃的光源方向与反射参数。", emptyList(), softGlass)
    page(Page.LiquidMaterial, "控制玻璃主体的基础材质感", groups(liquidMaterialGroups), glass)
    page(Page.LiquidRefraction, "折射形状、位移、边缘衰减与表面几何", groups(liquidRefractionGroups), glass)
    page(Page.LiquidColor, "染色保留背景纹理，底色可纯色覆盖；另可调整色散与饱和度", groups(liquidColorGroups), glass)
    page(Page.LiquidShadow, "玻璃内部阴影颜色与柔和程度", groups(liquidShadowGroups), glass)
    page(Page.LiquidLighting, "调节焦散、菲涅尔反射与光源方向，并集中调整边缘反射和方向光。",
        groups(liquidLightingGroups + liquidOs4Groups) + listOf(
            SettingsBlock.Header("lighting.edge", "外阴影与描边"),
            SettingsBlock.Link("lighting.edge.scope", text(R.string.page_glass_edge),
                "Prismal 外阴影与光学描边，按作用域（Dock、图标、小组件…）独立调整", Page.GlassEdge),
        ), glass)
    page(Page.LiquidSampling, "控制桌面玻璃的静止采样分辨率与动画期着色性能优化。",
        listOf(
            SettingsBlock.Header("sampling.performance", "实时背景采样与性能"),
            toggle(ConfigSchema.Glass.DISABLE_STATIONARY_DOWNSAMPLE, "取消桌面玻璃静止时的下采样",
                "禁用桌面原生在玻璃静止时的采样降级与下采样，保持完整采样分辨率；实时生效"),
            toggle(ConfigSchema.Glass.MOTION_DOWNSAMPLE, "滚动与动画时简化玻璃着色",
                "开启后在桌面滑动、打开文件夹等动画期间临时简化焦散等高开销着色以提升流畅度；关闭后动画期间保持完整着色；实时生效"),
        ), glass)
    numbers(Page.DockGeometry, "调整 Dock 的尺寸、位置、圆角与图标间距。", dockSpecs, dock)
    listOf(Page.GridLandscape to "landscape", Page.GridPortrait to "portrait").forEach { (p, orientation) ->
        val specs = gridSpecs.filter { it.key.startsWith("grid_$orientation") || it.key == "indicator_${orientation}_y" }
        page(p, "调整${if (orientation == "landscape") "横" else "竖"}屏桌面的间距与页面指示器位置。", buildList {
            add(SettingsBlock.Header("grid.position", "工作区位置"))
            addAll(specs.filterNot { it.key.startsWith("indicator_") }.map { row(it) })
            add(SettingsBlock.Header("grid.indicator", "页面指示器"))
            addAll(specs.filter { it.key.startsWith("indicator_") }.map { row(it) })
        }, grid)
    }
    page(Page.WorkstationDock, "调整工作台 Dock 的外观与图标位置。", listOf(
        toggle(ConfigSchema.Workstation.TASKBAR_MODE, "任务栏模式",
            "开启后 Dock 立即贴底并向左右与底部三边延伸；高度调节以底部边缘为底线并保持图标垂直居中，仍支持垂直偏移调节。"),
    ) + workstationDockSpecs.map { spec ->
        val req = if (spec.key == ConfigSchema.Workstation.DOCK_HEIGHT_OFFSET.name()) {
            listOf(ConfigSchema.Workstation.TASKBAR_MODE to true)
        } else emptyList()
        row(spec, req)
    }, workstation)
    numbers(Page.WorkstationAppsLandscape, "所有应用横屏布局参数", workstationAppsLandscapeSpecs, workstation)
    numbers(Page.WorkstationAppsPortrait, "所有应用竖屏布局参数", workstationAppsPortraitSpecs, workstation)
    page(Page.WorkstationDesktop, "设置工作台桌面边距与页面指示器位置。", buildList {
        add(toggle(ConfigSchema.Workstation.DESKTOP_SYNC_NORMAL, "同步普通桌面布局",
            "开启后工作台桌面直接使用普通桌面的横竖屏边距与指示器设置；关闭后可独立调节工作台桌面参数"))
        val customDesktop = workstation + (ConfigSchema.Workstation.DESKTOP_SYNC_NORMAL to false)
        add(SettingsBlock.Header("ws.desktop.land", "横屏布局"))
        addAll(workstationDesktopLandscapeSpecs.map { row(it, customDesktop) })
        add(SettingsBlock.Header("ws.desktop.port", "竖屏布局"))
        addAll(workstationDesktopPortraitSpecs.map { row(it, customDesktop) })
    }, workstation)
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
        row(folderBlurSpec),
        row(allAppsBlurSpec),
        row(wsAllAppsBlurSpec),
        row(shortcutBlurSpec),
    ))
    page(Page.AnimationSystem, "控制通知与控制中心顶部模糊及系统界面联动。", listOf(
        toggle(ConfigSchema.Animation.SYSTEM_UI_HEADER_GRADIENT_BLUR,
            "控制中心/通知中心顶部渐变模糊",
            "只影响面板顶部渐变模糊，不改变面板主体背景模糊；需启用 LiquidDock4 的 SystemUI 作用域，切换后重启系统界面生效"),
        toggle(ConfigSchema.Animation.HIDE_GESTURE_HANDLE_HOME_RECENTS, "桌面/多任务隐藏手势小白条",
            "进入桌面或多任务后隐藏手势手柄，离开后交还系统原生显示时序；开关即时生效，首次安装此版本需重启系统界面"),
        toggle(ConfigSchema.Glass.WALLPAPER_FLICKER_FIX, "壁纸闪烁 / 抖动修复",
            "在 system 作用域内将桌面壁纸强制为客户端合成，缓解桌面动画或过渡时的闪烁与抖动；需勾选 system 作用域并重启设备生效"),
    ))
    page(Page.GlassMenus, "菜单与弹窗按组件设置，系统级壁纸修复独立放置。", listOf(
        SettingsBlock.Header("menus.heading", "菜单与弹窗"),
        SettingsBlock.Link("menus.handle", text(R.string.page_handle_menu), "应用顶部控制器的菜单玻璃、颜色、模糊与独立光学参数", Page.HandleMenuCustomization),
        SettingsBlock.Link("menus.shortcut", text(R.string.page_shortcut_popup), "长按快捷菜单的玻璃、深色模式、模糊、颜色及独立光学参数", Page.ShortcutPopupCustomization),
        SettingsBlock.Link("menus.dialog", text(R.string.page_dialog_customization), "卸载、移除与二次确认弹窗的玻璃、背景压暗、模糊和颜色", Page.DialogCustomization),
    ), glass)
    glassEdgePageScopes.forEach { (p, scope) ->
        val label = glassEdgeScopeLabels.getValue(scope)
        val global = scope == GlassEdgeEffects.GLOBAL
        val iconNumbersOs4Unavailable = scope == GlassEdgeEffects.ICON
        fun edgeNumber(config: ConfigKey<Int>, title: String, unit: String = ""): SettingsBlock.Row {
            val spec = IntSpec(config, title, unit)
            val key = if (global) config.name() else GlassEdgeEffects.key(scope, config)
            return SettingsBlock.Row(
                key, key, title, spec.summary,
                number = spec,
                scope = if (global) null else scope,
                os4Unavailable = iconNumbersOs4Unavailable,
            )
        }
        fun edgeToggle(config: ConfigKey<Boolean>, title: String, summary: String): SettingsBlock.Row {
            val key = if (global) config.name() else GlassEdgeEffects.key(scope, config)
            return SettingsBlock.Row(key, key, title, summary, toggle = config, scope = if (global) null else scope)
        }
        val blocks = buildList {
            val scopeNote = when {
                global -> "各组件可继承全局参数或独立调整。"
                scope == GlassEdgeEffects.ICON -> "当前仅支持独立开关折射描边与外阴影，其余数值参数继承全局默认。"
                else -> "本页未单独设置的参数继承全局默认。"
            }
            add(SettingsBlock.Info("edge.intro", "$label · 设置液态玻璃的 Prismal 外阴影，以及沿玻璃轮廓的折射描边。仅在对应组件启用液态玻璃时生效。$scopeNote"))
            add(SettingsBlock.Header("edge.backlight", "玻璃边缘光照"))
            add(edgeToggle(ConfigSchema.Glass.BACKLIT_ENABLED, "启用折射光照描边", "同一条描边随折射背景与方向光照连续变色"))
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
