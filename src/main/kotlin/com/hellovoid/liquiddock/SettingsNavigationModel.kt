package com.hellovoid.liquiddock

// One source of truth for settings routes and the stable hub menu catalogs.
// The Activity retains navigation history, back actions and page dispatch.
internal enum class Page(val titleRes: Int) {
    Home(R.string.app_name),
    LayoutHub(R.string.page_layout_hub),
    GlassHub(R.string.page_glass_hub),
    MoreHub(R.string.page_more_hub),

    Grid(R.string.page_grid),
    GridBasics(R.string.page_grid_basics),
    GridLandscape(R.string.page_grid_landscape),
    GridPortrait(R.string.page_grid_portrait),
    GridSplit(R.string.page_grid_split),

    Dock(R.string.page_dock),
    DockBehavior(R.string.page_dock_behavior),
    DockGeometry(R.string.page_dock_geometry),
    DockRecentBlacklist(R.string.page_dock_recent_blacklist),
    Divider(R.string.page_divider),
    Workstation(R.string.page_workstation),
    WorkstationDock(R.string.page_workstation_dock),
    WorkstationDesktop(R.string.page_workstation_desktop),
    WorkstationAppsLandscape(R.string.page_workstation_apps_landscape),
    WorkstationAppsPortrait(R.string.page_workstation_apps_portrait),
    Recents(R.string.page_recents),

    Liquid(R.string.page_liquid),
    LiquidMaterial(R.string.page_liquid_material),
    LiquidRefraction(R.string.page_liquid_refraction),
    LiquidColor(R.string.page_liquid_color),
    LiquidLighting(R.string.page_liquid_lighting),
    LiquidShadow(R.string.page_liquid_shadow),
    LiquidSampling(R.string.page_liquid_sampling),
    LiquidOs4(R.string.page_liquid_os4),

    GlassComponents(R.string.page_glass_components),
    GlassIcons(R.string.page_glass_icons),
    GlassWidgets(R.string.page_glass_widgets),
    GlassFolders(R.string.page_glass_folders),
    GlassMenus(R.string.page_glass_menus),
    DialogCustomization(R.string.page_dialog_customization),
    ThirdPartyApps(R.string.page_third_party_apps),
    Gboard(R.string.page_gboard),
    GboardAll(R.string.page_gboard),
    DialogAll(R.string.page_dialog_customization),
    WidgetComponents(R.string.page_widget_components),
    LauncherHighlights(R.string.page_launcher_highlights),
    LauncherHighlightsCompact(R.string.page_launcher_highlights_compact),
    LauncherHighlightsLarge(R.string.page_launcher_highlights_large),
    Stroke(R.string.page_stroke),
    Shadow(R.string.page_shadow),

    SecurityCenterSidebar(R.string.page_security_center_sidebar),
    SecurityCenterDock(R.string.page_security_center_dock),
    SecurityCenterAllApps(R.string.page_security_center_all_apps),
    SecurityCenterGameToolbox(R.string.page_security_center_game_toolbox),
    SecurityCenterVideoToolbox(R.string.page_security_center_video_toolbox),
    Animation(R.string.page_animation),
    AnimationWorkspace(R.string.page_animation_workspace),
    AnimationInteraction(R.string.page_animation_interaction),
    AnimationPopups(R.string.page_animation_popups),
    AnimationSystem(R.string.page_animation_system),
    AnimationGui(R.string.page_animation_gui),
    Data(R.string.page_data),
    About(R.string.page_about),
}

internal val ROOT_PAGES = listOf(Page.Home, Page.LayoutHub, Page.GlassHub, Page.MoreHub)

internal fun isRootPage(page: Page): Boolean = page in ROOT_PAGES

// Ordinary UI writes are mirrored to API101 Remote Preferences by LiquidDockApp's
// SharedPreferences listener. No per-control JSON/file/root synchronization exists.

// Settings option catalogs live in SettingsOptionSpecs.kt; navigation and rendering stay here.

internal data class HubEntry(
    val page: Page,
    val titleRes: Int,
    val summary: String,
)

internal val overviewEntries = listOf(
    HubEntry(Page.Dock, R.string.page_dock, "Dock 外观、位置、动画与最近程序"),
    HubEntry(Page.Liquid, R.string.page_liquid, "材质、折射、色彩、光照与性能"),
    HubEntry(Page.Grid, R.string.page_grid, "网格尺寸、横竖屏与分屏布局"),
    HubEntry(Page.Animation, R.string.page_animation, "工作区、Dock、玻璃交互与系统界面"),
)

internal val layoutEntries = listOf(
    HubEntry(Page.Grid, R.string.page_grid, "网格尺寸、横竖屏间距与分屏布局"),
    HubEntry(Page.Dock, R.string.page_dock, "Dock 行为、尺寸、位置与最近程序"),
    HubEntry(Page.Workstation, R.string.page_workstation, "工作台桌面、Dock 与所有应用布局"),
    HubEntry(Page.Recents, R.string.page_recents, "多任务壁纸背景与模糊"),
    HubEntry(Page.SecurityCenterSidebar, R.string.page_security_center_sidebar, "侧滑呼出、工具箱与侧边栏设置"),
)

internal val glassEntries = listOf(
    HubEntry(Page.Liquid, R.string.page_liquid, "液态玻璃材质与光学参数"),
    HubEntry(Page.GlassComponents, R.string.page_glass_components, "图标、小组件、文件夹与菜单外观"),
    HubEntry(Page.Stroke, R.string.page_stroke, "Dock 描边、方圆曲线与颜色"),
    HubEntry(Page.Shadow, R.string.page_shadow, "Dock 与描边阴影"),
    HubEntry(Page.Animation, R.string.page_animation, "工作区、Dock 与玻璃交互动画"),
    HubEntry(Page.ThirdPartyApps, R.string.page_third_party_apps, "Gboard 等第三方应用适配"),
)

internal val moreEntries = listOf(
    HubEntry(Page.Data, R.string.page_data, "默认配置、导入与导出"),
    HubEntry(Page.About, R.string.page_about, "第三方开源项目与许可"),
)

internal val gridEntries = listOf(
    HubEntry(Page.GridBasics, R.string.page_grid_basics, "图标大小、网格尺寸与小组件拉伸"),
    HubEntry(Page.GridLandscape, R.string.page_grid_landscape, "横屏距离、行距与页面指示器"),
    HubEntry(Page.GridPortrait, R.string.page_grid_portrait, "竖屏距离、行距与页面指示器"),
    HubEntry(Page.GridSplit, R.string.page_grid_split, "分屏布局距离与对齐"),
)

internal val dockEntries = listOf(
    HubEntry(Page.DockBehavior, R.string.page_dock_behavior, "功能开关、尺寸动画与系统入口"),
    HubEntry(Page.DockGeometry, R.string.page_dock_geometry, "高度、宽度、圆角、间距与底部位置"),
    HubEntry(Page.DockRecentBlacklist, R.string.page_dock_recent_blacklist, "过滤最近程序，同时由后续候选补位"),
    HubEntry(Page.Divider, R.string.page_divider, "分隔线尺寸、位置、颜色与透明度"),
)

internal val workstationEntries = listOf(
    HubEntry(Page.WorkstationDock, R.string.page_workstation_dock, "Dock 图标垂直偏移与玻璃圆角"),
    HubEntry(Page.WorkstationDesktop, R.string.page_workstation_desktop, "按当前网格列数整体调整工作台桌面水平位置"),
    HubEntry(Page.WorkstationAppsLandscape, R.string.page_workstation_apps_landscape, "所有应用横屏水平与上下间距"),
    HubEntry(Page.WorkstationAppsPortrait, R.string.page_workstation_apps_portrait, "所有应用竖屏水平与上下间距"),
)

internal val liquidEntries = listOf(
    HubEntry(Page.LiquidMaterial, R.string.page_liquid_material, "模糊、厚度、透射率与亮度"),
    HubEntry(Page.LiquidRefraction, R.string.page_liquid_refraction, "折射、法线、穹顶与位移"),
    HubEntry(Page.LiquidColor, R.string.page_liquid_color, "底色、色散与鲜艳度"),
    HubEntry(Page.LiquidLighting, R.string.page_liquid_lighting, "高光、边缘光、焦散与光源方向"),
    HubEntry(Page.LiquidShadow, R.string.page_liquid_shadow, "玻璃内部阴影颜色与柔和度"),
    HubEntry(Page.LiquidSampling, R.string.page_liquid_sampling, "背景缩放、帧率与帧同步"),
    HubEntry(Page.LiquidOs4, R.string.page_liquid_os4, "OS4 边缘反射与方向光"),
)

internal val componentEntries = listOf(
    HubEntry(Page.GlassIcons, R.string.page_glass_icons, "桌面图标、Dock 功能图标与多任务胶囊"),
    HubEntry(Page.GlassWidgets, R.string.page_glass_widgets, "小组件玻璃、内容适配与组件隐藏"),
    HubEntry(Page.GlassFolders, R.string.page_glass_folders, "大小文件夹玻璃与独立尺寸"),
    HubEntry(Page.GlassMenus, R.string.page_glass_menus, "快捷菜单、系统顶部菜单与对话弹窗"),
)

internal val highlightEntries = listOf(
    HubEntry(Page.LauncherHighlightsCompact, R.string.page_launcher_highlights_compact, "天空雾光、镜面高光、边缘光、焦散与按压辉光"),
    HubEntry(Page.LauncherHighlightsLarge, R.string.page_launcher_highlights_large, "小组件与大文件夹使用独立高光开关"),
)

internal val animationEntries = listOf(
    HubEntry(Page.AnimationWorkspace, R.string.page_animation_workspace, "工作区显隐、Dock 恢复与尺寸变化"),
    HubEntry(Page.AnimationInteraction, R.string.page_animation_interaction, "玻璃按压进入与释放"),
    HubEntry(Page.AnimationPopups, R.string.page_animation_popups, "快捷菜单与安全中心退出渐隐"),
    HubEntry(Page.AnimationSystem, R.string.page_animation_system, "系统手势手柄等界面联动"),
    HubEntry(Page.AnimationGui, R.string.page_animation_gui, "设置页面切换时长"),
)

// Animation option specs live in SettingsOptionSpecs.kt.

