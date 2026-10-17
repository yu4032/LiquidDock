package com.hellovoid.liquiddock

import com.hellovoid.liquiddock.config.ConfigKey
import com.hellovoid.liquiddock.config.ConfigSchema

/** The slider's -1 value means "follow the current global Prismal material". */
internal data class SidebarSceneSettings(
    val page: Page,
    val title: String,
    val enabled: ConfigKey<Boolean>,
    val specs: List<IntSpec>,
)

private fun sidebarScene(
    page: Page,
    title: String,
    enabled: ConfigKey<Boolean>,
    blur: ConfigKey<Int>,
    red: ConfigKey<Int>,
    green: ConfigKey<Int>,
    blue: ConfigKey<Int>,
    alpha: ConfigKey<Int>,
): SidebarSceneSettings = SidebarSceneSettings(
    page, title, enabled, listOf(
        IntSpec(blur, "模糊半径", "px", summary = "−1 跟随全局玻璃模糊；其他值覆盖当前场景"),
        IntSpec(red, "颜色 · 红", "", summary = "−1 跟随全局玻璃颜色；0–255 为独立分量"),
        IntSpec(green, "颜色 · 绿", "", summary = "−1 跟随全局玻璃颜色；0–255 为独立分量"),
        IntSpec(blue, "颜色 · 蓝", "", summary = "−1 跟随全局玻璃颜色；0–255 为独立分量"),
        IntSpec(alpha, "颜色强度", "", summary = "−1 跟随全局玻璃强度；0 无染色，255 最大强度"),
    ),
)

internal val sidebarSceneSettings = listOf(
    sidebarScene(Page.SecurityCenterDock, "侧边 Dock", ConfigSchema.SecurityCenterScene.DOCK_ENABLED,
        ConfigSchema.SecurityCenterScene.DOCK_BLUR,
        ConfigSchema.SecurityCenterScene.DOCK_TINT_RED,
        ConfigSchema.SecurityCenterScene.DOCK_TINT_GREEN,
        ConfigSchema.SecurityCenterScene.DOCK_TINT_BLUE,
        ConfigSchema.SecurityCenterScene.DOCK_TINT_ALPHA),
    sidebarScene(Page.SecurityCenterAllApps, "All Apps", ConfigSchema.SecurityCenterScene.ALL_APPS_ENABLED,
        ConfigSchema.SecurityCenterScene.ALL_APPS_BLUR,
        ConfigSchema.SecurityCenterScene.ALL_APPS_TINT_RED,
        ConfigSchema.SecurityCenterScene.ALL_APPS_TINT_GREEN,
        ConfigSchema.SecurityCenterScene.ALL_APPS_TINT_BLUE,
        ConfigSchema.SecurityCenterScene.ALL_APPS_TINT_ALPHA),
    sidebarScene(Page.SecurityCenterGameToolbox, "游戏工具箱", ConfigSchema.SecurityCenterScene.GAME_TOOLBOX_ENABLED,
        ConfigSchema.SecurityCenterScene.GAME_TOOLBOX_BLUR,
        ConfigSchema.SecurityCenterScene.GAME_TOOLBOX_TINT_RED,
        ConfigSchema.SecurityCenterScene.GAME_TOOLBOX_TINT_GREEN,
        ConfigSchema.SecurityCenterScene.GAME_TOOLBOX_TINT_BLUE,
        ConfigSchema.SecurityCenterScene.GAME_TOOLBOX_TINT_ALPHA),
    sidebarScene(Page.SecurityCenterVideoToolbox, "视频工具箱", ConfigSchema.SecurityCenterScene.VIDEO_TOOLBOX_ENABLED,
        ConfigSchema.SecurityCenterScene.VIDEO_TOOLBOX_BLUR,
        ConfigSchema.SecurityCenterScene.VIDEO_TOOLBOX_TINT_RED,
        ConfigSchema.SecurityCenterScene.VIDEO_TOOLBOX_TINT_GREEN,
        ConfigSchema.SecurityCenterScene.VIDEO_TOOLBOX_TINT_BLUE,
        ConfigSchema.SecurityCenterScene.VIDEO_TOOLBOX_TINT_ALPHA),
)
