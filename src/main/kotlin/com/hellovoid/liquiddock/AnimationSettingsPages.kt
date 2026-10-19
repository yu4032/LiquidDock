package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hellovoid.liquiddock.config.ConfigSchema
import top.yukonga.miuix.kmp.basic.SmallTitle

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
    DenseSettingsList(
        padding,
        stringResource(R.string.page_animation_workspace),
        "按工作区和 Dock 分别调整玻璃过渡时间。",
    ) {
        item(key = "animation-workspace-heading") { SmallTitle("工作区与文件夹") }
        item(key = "animation-workspace") {
            SettingsCard {
                IntSetting(prefs, animationWorkspaceVisibilitySpec, masterEnabled)
                ModernListDivider()
                IntSetting(prefs, animationUnlockGlassFadeSpec, masterEnabled)
                ModernListDivider()
                IntSetting(prefs, animationFolderOpenGlassFadeSpec, masterEnabled)
            }
        }
        item(key = "animation-dock-heading") { SmallTitle("Dock 与图标") }
        item(key = "animation-dock") {
            SettingsCard {
                IntSetting(
                    prefs,
                    animationDockResizeSpec,
                    masterEnabled && dockCustomizationEnabled && !systemResizeEnabled && smoothResizeEnabled,
                )
            }
        }
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
        ModernListDivider()
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
        ModernListDivider()
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

