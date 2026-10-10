package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hellovoid.liquiddock.config.ConfigSchema

// Leaf pages only. Navigation, page history and Prismal chrome stay in ComposeSettingsActivity.
// Use the shared IntSetting/SettingsList controls to preserve preference and visual semantics.
@Composable
internal fun AnimationWorkspacePage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val dockCustomizationEnabled = prefs.getBoolean(
        ConfigSchema.Dock.ENABLED.name(),
        ConfigSchema.Dock.ENABLED.uiDefault(),
    )
    val systemResizeEnabled = prefs.getBoolean(
        ConfigSchema.Dock.RESIZE_ANIMATION.name(),
        ConfigSchema.Dock.RESIZE_ANIMATION.uiDefault(),
    )
    val smoothResizeEnabled = prefs.getBoolean(
        ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION.name(),
        ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION.uiDefault(),
    )
    SettingsList(
        padding,
        stringResource(R.string.page_animation_workspace),
        "调整工作区与 Dock 的玻璃过渡动画。",
    ) {
        IntSetting(prefs, animationWorkspaceVisibilitySpec, masterEnabled)
        IntSetting(prefs, animationDockIconRevealSpec, masterEnabled)
        IntSetting(
            prefs,
            animationDockResizeSpec,
            masterEnabled && dockCustomizationEnabled && !systemResizeEnabled && smoothResizeEnabled,
        )
    }
}

@Composable
internal fun AnimationInteractionPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    SettingsList(
        padding,
        stringResource(R.string.page_animation_interaction),
        "玻璃按下与释放反馈独立调节；0 ms 表示立即完成。",
    ) {
        IntSetting(prefs, animationPressInSpec, masterEnabled)
        IntSetting(prefs, animationPressOutSpec, masterEnabled)
    }
}

@Composable
internal fun AnimationPopupsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    SettingsList(
        padding,
        stringResource(R.string.page_animation_popups),
        "调整快捷菜单与安全中心玻璃的退出渐隐。",
    ) {
        IntSetting(prefs, animationShortcutDismissSpec, masterEnabled)
        IntSetting(prefs, animationSecurityCenterExitSpec, masterEnabled)
    }
}

@Composable
internal fun AnimationSystemPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    SettingsList(
        padding,
        stringResource(R.string.page_animation_system),
        "系统界面联动单独放置，避免与普通玻璃动画混在同一页。",
    ) {
        BooleanSetting(
            prefs,
            ConfigSchema.Animation.HIDE_GESTURE_HANDLE_HOME_RECENTS,
            "桌面/多任务隐藏手势小白条",
            "进入桌面或多任务后隐藏手势手柄，离开后交还系统原生显示时序；开关即时生效，首次安装此版本需重启系统界面",
            masterEnabled,
        )
    }
}

@Composable
internal fun AnimationGuiPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    SettingsList(
        padding,
        stringResource(R.string.page_animation_gui),
        "调整设置页面的切换动画。",
    ) {
        IntSetting(prefs, animationSettingsPageSpec, masterEnabled)
    }
}

