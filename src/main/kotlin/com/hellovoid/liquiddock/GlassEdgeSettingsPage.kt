package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hellovoid.liquiddock.config.ConfigKey
import com.hellovoid.liquiddock.config.ConfigSchema
import top.yukonga.miuix.kmp.basic.SmallTitle

/**
 * Unified Prismal/OS4 glass lighting and whole-silhouette projection.
 * Backlight is multiplicative optical attenuation, never a painted dark stroke.
 * Component-local preferences inherit absent values from the global material.
 */
@Composable
internal fun GlassEdgeSettingsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    scope: String,
    open: (Page) -> Unit = {},
) {
    val global = scope == GlassEdgeEffects.GLOBAL
    val nativeShadowOwner = scope == GlassEdgeEffects.DOCK
        || scope == GlassEdgeEffects.HANDLE_MENU
        || scope == GlassEdgeEffects.DIALOG
        || scope == GlassEdgeEffects.SECURITY_CENTER
    val active = masterEnabled && prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(), ConfigSchema.Glass.ENABLED.uiDefault())
    var revision by remember(scope) { mutableIntStateOf(0) }
    var resetGeneration by remember(scope) { mutableIntStateOf(0) }
    val hasOverrides = revision.let {
        !global && GlassEdgeEffects.KEYS.any { config ->
            val key = GlassEdgeEffects.key(scope, config)
            prefs.contains(key) || prefs.contains("${key}_tenths")
        }
    }

    @Composable
    fun toggle(config: ConfigKey<Boolean>, title: String, info: String) {
        if (global) {
            BooleanSetting(prefs, config, title, info, active)
        } else {
            val key = GlassEdgeEffects.key(scope, config)
            val currentRevision = LocalSettingsPreferenceRevisions.current[key] ?: 0
            var value by remember(key, resetGeneration, currentRevision) {
                mutableStateOf(prefs.getBoolean(
                    key, prefs.getBoolean(config.name(), config.uiDefault())))
            }
            SwitchPreference(
                checked = value,
                onCheckedChange = {
                    value = it
                    prefs.edit().putBoolean(key, it).apply()
                    revision++
                },
                title = title,
                summary = if (prefs.contains(key)) "独立设置 · $info" else "继承全局 · $info",
                enabled = active,
            )
        }
    }

    @Composable
    fun number(config: ConfigKey<Int>, title: String, unit: String = "") {
        if (global) {
            IntSetting(prefs, IntSpec(config, title, unit), active)
        } else {
            ScopedGlassSlider(
                prefs = prefs,
                key = GlassEdgeEffects.key(scope, config),
                fallback = config,
                title = title,
                unit = unit,
                enabled = active,
                generation = resetGeneration,
                decimal = config.storageMode() == ConfigKey.StorageMode.DP_TENTHS,
                onPersisted = { if (!hasOverrides) revision++ },
            )
        }
    }

    val scopeLabel = when (scope) {
        GlassEdgeEffects.GLOBAL -> "全局默认"
        GlassEdgeEffects.DOCK -> "Dock 本体"
        GlassEdgeEffects.ICON -> "桌面图标"
        GlassEdgeEffects.WIDGET -> "小组件"
        GlassEdgeEffects.FOLDER -> "文件夹"
        GlassEdgeEffects.POPUP -> "快捷菜单"
        GlassEdgeEffects.HANDLE_MENU -> "顶部菜单"
        GlassEdgeEffects.DIALOG -> "对话弹窗"
        GlassEdgeEffects.GBOARD -> "Gboard 浮层"
        GlassEdgeEffects.SEARCHBOX -> "搜索框"
        GlassEdgeEffects.SECURITY_CENTER -> "安全中心"
        else -> scope
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item(key = "material-intro") {
            PageHeader(
                "玻璃边缘光照与投影",
                "$scopeLabel · Prismal OS4 的高光、反射与折射保持不变。" +
                    "背光边缘仅轻微减弱透射，不绘制独立黑色描边。" +
                    (if (nativeShadowOwner) "此组件保留原生整体阴影，不叠加 GPU 投影。"
                     else if (global) "以下参数可由各个组件继承。"
                     else "本页缺省参数继承全局默认。")
            )
        }
        item(key = "backlight-header") { SmallTitle("玻璃边缘光照") }
        item(key = "backlight-on") {
            SettingsCard {
                toggle(
                    ConfigSchema.Glass.BACKLIT_ENABLED,
                    "启用背光边缘微暗",
                    "受光侧继续使用 OS4 高光；背光侧按法线与实际照度平滑压暗",
                )
            }
        }
        item(key = "backlight-levels") {
            SettingsCard {
                number(ConfigSchema.Glass.BACKLIT_STRENGTH, "背光微暗强度", "%")
                number(ConfigSchema.Glass.BACKLIT_WIDTH, "边缘响应宽度", "dp")
                number(ConfigSchema.Glass.BACKLIT_SOFTNESS, "光照过渡柔和度", "%")
            }
        }
        if (!nativeShadowOwner) {
            item(key = "shadow-header") { SmallTitle("整块玻璃的柔和投影") }
            item(key = "shadow-on") {
                SettingsCard {
                    toggle(
                        ConfigSchema.Glass.OUTER_SHADOW_ENABLED,
                        "启用整体外阴影",
                        "以整个圆角玻璃轮廓投影，不模拟黑色轮廓线",
                    )
                }
            }
            item(key = "shadow-shape") {
                SettingsCard {
                    number(ConfigSchema.Glass.OUTER_SHADOW_RADIUS, "阴影柔和半径", "dp")
                    number(ConfigSchema.Glass.OUTER_SHADOW_OFFSET_Y, "基础垂直偏移", "dp")
                    number(ConfigSchema.Glass.OUTER_SHADOW_LIGHT_FOLLOW, "方向光响应", "%")
                }
            }
            item(key = "shadow-color") {
                SettingsCard {
                    number(ConfigSchema.Glass.OUTER_SHADOW_ALPHA, "阴影不透明度")
                    number(ConfigSchema.Glass.OUTER_SHADOW_RED, "阴影红")
                    number(ConfigSchema.Glass.OUTER_SHADOW_GREEN, "阴影绿")
                    number(ConfigSchema.Glass.OUTER_SHADOW_BLUE, "阴影蓝")
                }
            }
        }
        if (global) {
            item(key = "scope-title") { SmallTitle("各玻璃组件的独立设置") }
            item(key = "scope-launcher") {
                SettingsCard {
                    ArrowPreference("Dock 本体", "保留系统原生 MiShadow",
                        enabled = active, onClick = { open(Page.DockEdge) })
                    ArrowPreference("桌面图标", "图标和多任务胶囊",
                        enabled = active, onClick = { open(Page.IconEdge) })
                    ArrowPreference("小组件", "宽大玻璃表面的柔和光学变化",
                        enabled = active, onClick = { open(Page.WidgetEdge) })
                    ArrowPreference("文件夹", "小文件夹与展开文件夹",
                        enabled = active, onClick = { open(Page.FolderEdge) })
                }
            }
            item(key = "scope-menus") {
                SettingsCard {
                    ArrowPreference("快捷菜单", "长按应用菜单",
                        enabled = active, onClick = { open(Page.PopupEdge) })
                    ArrowPreference("顶部菜单", "保留原生窗口阴影",
                        enabled = active, onClick = { open(Page.HandleMenuEdge) })
                    ArrowPreference("对话弹窗", "保留系统阴影",
                        enabled = active, onClick = { open(Page.DialogEdge) })
                }
            }
            item(key = "scope-more") {
                SettingsCard {
                    ArrowPreference("Gboard", "浮层玻璃",
                        enabled = active, onClick = { open(Page.GboardEdge) })
                    ArrowPreference("搜索框", "系统搜索框",
                        enabled = active, onClick = { open(Page.SearchboxEdge) })
                    ArrowPreference("安全中心", "保留已有窗口阴影",
                        enabled = active, onClick = { open(Page.SecurityCenterEdge) })
                }
            }
        }
        if (!global) {
            item(key = "restore-inheritance") {
                SettingsCard {
                    ArrowPreference(
                        title = "恢复继承全局光照参数",
                        summary = "仅清除该组件的覆盖参数，其他玻璃不受影响",
                        enabled = active && hasOverrides,
                        onClick = {
                            val editor = prefs.edit()
                            GlassEdgeEffects.KEYS.forEach { config ->
                                val key = GlassEdgeEffects.key(scope, config)
                                editor.remove(key).remove("${key}_tenths")
                            }
                            editor.apply()
                            revision++
                            resetGeneration++
                        },
                    )
                }
            }
        }
    }
}
