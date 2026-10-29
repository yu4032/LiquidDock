package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hellovoid.liquiddock.config.ConfigSchema
import top.yukonga.miuix.kmp.basic.SmallTitle

/** Independent backend selector. Component settings remain owned by their existing pages. */
@Composable
internal fun NativeAdvancedMaterialSettingsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
    searchFocus: SettingsFocusRequest? = null,
) {
    val nativeKey = ConfigSchema.Glass.ADVANCED_MATERIAL_ENABLED.name()
    val revision = LocalSettingsPreferenceRevisions.current[nativeKey] ?: 0
    val liquidRevision = LocalSettingsPreferenceRevisions.current[
        ConfigSchema.Glass.ENABLED.name()] ?: 0
    var nativeEnabled by remember(revision, liquidRevision) {
        mutableStateOf(prefs.getBoolean(nativeKey, false))
    }
    DenseSettingsList(
        padding, "高级材质",
        "使用系统原生模糊与圆角，不叠加液态玻璃。切换材质后需重启桌面及受影响的应用。",
        focusItemIndex = searchFocus?.let { customDenseFocusIndex(Page.AdvancedMaterial, it.key) },
        focusSequence = searchFocus?.sequence,
    ) {
        item(key = "native-material-title") { SmallTitle("渲染模式") }
        item(key = "native-material-toggle") {
            SettingsCard {
                SwitchPreference(
                    checked = nativeEnabled,
                    onCheckedChange = { next ->
                        // Atomic write: no intermediate frame can select both pipelines.
                        prefs.edit()
                            .putBoolean(ConfigSchema.Glass.ADVANCED_MATERIAL_ENABLED.name(), next)
                            .putBoolean(ConfigSchema.Glass.ENABLED.name(), false)
                            .apply()
                        nativeEnabled = next
                    },
                    title = "启用高级材质",
                    summary = "与液态玻璃互斥。关闭后恢复普通系统样式，重启桌面生效",
                    enabled = masterEnabled,
                )
            }
        }
        item(key = "native-material-blur-title") { SmallTitle("系统模糊") }
        item(key = "native-material-blur") {
            SettingsCard {
                IntSetting(prefs, advancedMaterialBlurSpec, masterEnabled && nativeEnabled)
                ModernListDivider()
                BooleanSetting(
                    prefs, ConfigSchema.Glass.ADVANCED_MATERIAL_OVERRIDE_NATIVE_BLUR,
                    "覆盖系统材质模糊强度",
                    "默认保留 Launcher 原生强度；开启后向支持的原生背景写入上方模糊值",
                    masterEnabled && nativeEnabled,
                )
            }
        }
        item(key = "native-material-tint-title") { SmallTitle("原生染色") }
        item(key = "native-material-tint") {
            SettingsCard {
                IntSetting(prefs, advancedMaterialTintRedSpec, masterEnabled && nativeEnabled)
                IntSetting(prefs, advancedMaterialTintGreenSpec, masterEnabled && nativeEnabled)
                IntSetting(prefs, advancedMaterialTintBlueSpec, masterEnabled && nativeEnabled)
                IntSetting(prefs, advancedMaterialTintAlphaSpec, masterEnabled && nativeEnabled)
            }
        }
        item(key = "native-material-fill-title") { SmallTitle("原生底色") }
        item(key = "native-material-fill") {
            SettingsCard {
                IntSetting(prefs, advancedMaterialFillRedSpec, masterEnabled && nativeEnabled)
                IntSetting(prefs, advancedMaterialFillGreenSpec, masterEnabled && nativeEnabled)
                IntSetting(prefs, advancedMaterialFillBlueSpec, masterEnabled && nativeEnabled)
                IntSetting(prefs, advancedMaterialFillAlphaSpec, masterEnabled && nativeEnabled)
            }
        }
        item(key = "native-material-shadow-title") { SmallTitle("原生阴影") }
        item(key = "native-material-shadow") {
            SettingsCard {
                IntSetting(prefs, advancedMaterialShadowAlphaSpec, masterEnabled && nativeEnabled)
                IntSetting(prefs, advancedMaterialShadowRadiusSpec, masterEnabled && nativeEnabled)
            }
        }
        item(key = "native-material-components-title") { SmallTitle("组件设置") }
        item(key = "native-material-components") {
            ModernFeatureCard(
                title = "组件与圆角",
                summary = "复用图标和多任务按钮开关及圆角；小组件、文件夹保留系统自身的高级材质",
                onClick = { open(Page.GlassComponents) },
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
        item(key = "native-material-dock") {
            ModernFeatureCard(
                title = "Dock 尺寸与位置",
                summary = "继续使用原有 Dock 自定义；高级材质保留系统动态背景和动画",
                onClick = { open(Page.DockGeometry) },
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
    }
}
