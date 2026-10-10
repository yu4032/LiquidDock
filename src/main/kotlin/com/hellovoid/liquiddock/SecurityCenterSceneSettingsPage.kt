package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.hellovoid.liquiddock.config.ConfigSchema
import top.yukonga.miuix.kmp.basic.SmallTitle

/** Focused scene settings retain existing ConfigSchema keys and shared list spacing. */
@Composable
internal fun SecurityCenterSceneSettingsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    scene: SidebarSceneSettings,
) {
    val globalGlassEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(), ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    val scGlassEnabled = prefs.getBoolean(
        ConfigSchema.Glass.SECURITY_CENTER_GLASS.name(),
        ConfigSchema.Glass.SECURITY_CENTER_GLASS.uiDefault(),
    )
    val enabledRevision = LocalSettingsPreferenceRevisions.current[scene.enabled.name()] ?: 0
    var sceneEnabled by remember(scene.enabled.name(), enabledRevision) {
        mutableStateOf(prefs.getBoolean(scene.enabled.name(), scene.enabled.uiDefault()))
    }
    val canEdit = masterEnabled && globalGlassEnabled && scGlassEnabled

    DenseSettingsList(
        padding = padding,
        title = stringResource(scene.page.titleRes),
        summary = "独立控制${scene.title}的玻璃外观；参数设为 −1 时跟随全局玻璃配置。",
    ) {
        item(key = "sc-scene-toggle") {
            SettingsCard {
                BooleanSetting(
                    prefs = prefs,
                    config = scene.enabled,
                    title = "${scene.title}玻璃",
                    summary = "关闭后此场景恢复系统原生材质，不影响其他场景",
                    enabled = canEdit,
                ) { sceneEnabled = it }
            }
        }
        item(key = "sc-scene-blur-label") { SmallTitle("模糊度") }
        item(key = "sc-scene-blur-control") {
            SettingsCard {
                IntSetting(prefs, scene.specs.first(), canEdit && sceneEnabled)
            }
        }
        item(key = "sc-scene-colors-label") { SmallTitle("颜色与强度") }
        groupedIntSettings(scene.specs.drop(1), prefs, canEdit && sceneEnabled)
    }
}
