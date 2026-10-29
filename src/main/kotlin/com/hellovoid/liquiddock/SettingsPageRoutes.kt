package com.hellovoid.liquiddock

/** Canonical ownership is independent of shortcut cards and traversal order. */
internal fun canonicalSettingsParent(page: Page): Page? = when (page) {
    Page.Home, Page.LayoutHub, Page.GlassHub, Page.MoreHub -> null
    Page.Grid, Page.Dock, Page.Workstation, Page.Recents, Page.SecurityCenterSidebar -> Page.LayoutHub
    Page.Liquid, Page.GlassComponents, Page.Stroke, Page.Shadow, Page.Animation, Page.ThirdPartyApps -> Page.GlassHub
    Page.ThemePalette, Page.Data, Page.About -> Page.MoreHub
    Page.GridBasics, Page.GridLandscape, Page.GridPortrait, Page.GridSplit -> Page.Grid
    Page.DockBehavior, Page.DockGeometry, Page.DockRecentBlacklist, Page.Divider -> Page.Dock
    Page.WorkstationDock, Page.WorkstationAppsLandscape, Page.WorkstationAppsPortrait -> Page.Workstation
    Page.AdvancedMaterial, Page.LiquidMaterial, Page.LiquidRefraction, Page.LiquidColor, Page.LiquidLighting,
    Page.LiquidShadow, Page.LiquidSampling, Page.GlassEdge -> Page.Liquid
    Page.GlassIcons, Page.GlassWidgets, Page.GlassFolders, Page.GlassMenus, Page.SidebarEdge -> Page.GlassComponents
    Page.HandleMenuCustomization, Page.ShortcutPopupCustomization, Page.DialogCustomization -> Page.GlassMenus
    Page.Gboard -> Page.ThirdPartyApps
    Page.GboardAll -> Page.Gboard
    Page.HandleMenuAll -> Page.HandleMenuCustomization
    Page.ShortcutPopupAll -> Page.ShortcutPopupCustomization
    Page.DialogAll -> Page.DialogCustomization
    Page.WidgetComponents -> Page.GlassWidgets
    Page.LauncherHighlights -> Page.GlassIcons
    Page.LauncherHighlightsCompact, Page.LauncherHighlightsLarge -> Page.LauncherHighlights
    Page.AnimationWorkspace, Page.AnimationInteraction, Page.AnimationPopups,
    Page.AnimationSystem, Page.AnimationGui -> Page.Animation
    Page.DockEdge, Page.IconEdge, Page.WidgetEdge, Page.FolderEdge, Page.PopupEdge,
    Page.HandleMenuEdge, Page.DialogEdge, Page.GboardEdge, Page.SearchboxEdge,
    Page.SecurityCenterEdge -> Page.GlassEdge
}

internal fun settingsPagePath(page: Page): List<Page> =
    canonicalSettingsParent(page)?.let { settingsPagePath(it) }.orEmpty() + page

internal val glassEdgePageScopes = linkedMapOf(
    Page.GlassEdge to GlassEdgeEffects.GLOBAL,
    Page.DockEdge to GlassEdgeEffects.DOCK,
    Page.IconEdge to GlassEdgeEffects.ICON,
    Page.WidgetEdge to GlassEdgeEffects.WIDGET,
    Page.FolderEdge to GlassEdgeEffects.FOLDER,
    Page.PopupEdge to GlassEdgeEffects.POPUP,
    Page.HandleMenuEdge to GlassEdgeEffects.HANDLE_MENU,
    Page.DialogEdge to GlassEdgeEffects.DIALOG,
    Page.GboardEdge to GlassEdgeEffects.GBOARD,
    Page.SearchboxEdge to GlassEdgeEffects.SEARCHBOX,
    Page.SecurityCenterEdge to GlassEdgeEffects.SECURITY_CENTER,
)

internal val glassEdgeScopeLabels = mapOf(
    GlassEdgeEffects.GLOBAL to "全局默认", GlassEdgeEffects.DOCK to "Dock 本体",
    GlassEdgeEffects.ICON to "桌面图标", GlassEdgeEffects.WIDGET to "小组件",
    GlassEdgeEffects.FOLDER to "文件夹", GlassEdgeEffects.POPUP to "快捷菜单",
    GlassEdgeEffects.HANDLE_MENU to "顶部菜单", GlassEdgeEffects.DIALOG to "对话弹窗",
    GlassEdgeEffects.GBOARD to "Gboard 浮层", GlassEdgeEffects.SEARCHBOX to "系统搜索",
    GlassEdgeEffects.SECURITY_CENTER to "安全中心侧边栏",
)
