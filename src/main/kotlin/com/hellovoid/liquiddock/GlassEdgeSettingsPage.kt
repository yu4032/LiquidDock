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
 * Shadow and outline are independent presentation effects, not Prismal's internal
 * shadow or the OS4 optical reflection. Scoped fields inherit by preference absence.
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
    val nativeShadowOwner = scope == GlassEdgeEffects.HANDLE_MENU
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
        item(key = "edge-intro") {
            PageHeader("外阴影与描边",
                "$scopeLabel · 外部阴影与真实描边独立控制，光源方向与 Prismal 一致。" +
                (if (nativeShadowOwner) "此组件保留系统窗口阴影，仅新增独立描边。"
                 else if (global) "其他组件可继承此配置。"
                 else "未覆盖时继承全局默认。"))
        }
        if (!nativeShadowOwner) {
        item(key = "shadow-label") { SmallTitle("外阴影") }
        item(key = "shadow-switch") {
            SettingsCard {
                toggle(ConfigSchema.Glass.OUTER_SHADOW_ENABLED, "启用外阴影",
                    "独立于玻璃内部阴影")
            }
        }
        item(key = "shadow-geometry") {
            SettingsCard {
                number(ConfigSchema.Glass.OUTER_SHADOW_RADIUS, "柔和半径", "dp")
                number(ConfigSchema.Glass.OUTER_SHADOW_OFFSET_Y, "垂直偏移", "dp")
                number(ConfigSchema.Glass.OUTER_SHADOW_LIGHT_FOLLOW, "光照方向跟随", "%")
            }
        }
        item(key = "shadow-color") {
            SettingsCard {
                number(ConfigSchema.Glass.OUTER_SHADOW_ALPHA, "阴影不透明度")
                number(ConfigSchema.Glass.OUTER_SHADOW_RED, "阴影红")
                number(ConfigSchema.Glass.OUTER_SHADOW_GREEN, "阴影绿")
            }
        }
        item(key = "shadow-color-blue") {
            SettingsCard {
                number(ConfigSchema.Glass.OUTER_SHADOW_BLUE, "阴影蓝")
            }
        }
        }
        item(key = "stroke-label") { SmallTitle("描边") }
        item(key = "stroke-switch") {
            SettingsCard {
                toggle(ConfigSchema.Glass.OUTER_STROKE_ENABLED, "启用独立描边",
                    "叠加于 Prismal 的自然光学边缘")
            }
        }
        item(key = "stroke-geometry") {
            SettingsCard {
                number(ConfigSchema.Glass.OUTER_STROKE_WIDTH, "侧边描边宽度", "dp")
                number(ConfigSchema.Glass.OUTER_STROKE_ANGLE_RANGE, "侧边描边延伸角度", "°")
                number(ConfigSchema.Glass.OUTER_STROKE_ALPHA, "描边不透明度")
                number(ConfigSchema.Glass.OUTER_STROKE_LIGHT_RESPONSE, "受光侧黑边抑制", "%")
            }
        }
        item(key = "stroke-color") {
            SettingsCard {
                number(ConfigSchema.Glass.OUTER_STROKE_RED, "描边红")
                number(ConfigSchema.Glass.OUTER_STROKE_GREEN, "描边绿")
                number(ConfigSchema.Glass.OUTER_STROKE_BLUE, "描边蓝")
            }
        }
        if (global) {
            item(key = "scope-title") { SmallTitle("组件独立设置") }
            item(key = "scope-launcher") {
                SettingsCard {
                    ArrowPreference(
                        "桌面图标", "图标与多任务胶囊",
                        enabled = active, onClick = { open(Page.IconEdge) })
                    ArrowPreference(
                        "小组件", "独立边缘效果",
                        enabled = active, onClick = { open(Page.WidgetEdge) })
                    ArrowPreference(
                        "文件夹", "大小文件夹和展开文件夹",
                        enabled = active, onClick = { open(Page.FolderEdge) })
                }
            }
            item(key = "scope-menu") {
                SettingsCard {
                    ArrowPreference(
                        "快捷菜单", "长按应用菜单",
                        enabled = active, onClick = { open(Page.PopupEdge) })
                    ArrowPreference(
                        "顶部菜单", "保留系统窗口原生投影",
                        enabled = active, onClick = { open(Page.HandleMenuEdge) })
                    ArrowPreference(
                        "对话弹窗", "保留原生窗口投影",
                        enabled = active, onClick = { open(Page.DialogEdge) })
                }
            }
            item(key = "scope-app") {
                SettingsCard {
                    ArrowPreference(
                        "Gboard", "输入法浮层玻璃",
                        enabled = active, onClick = { open(Page.GboardEdge) })
                    ArrowPreference(
                        "搜索框", "系统搜索框玻璃",
                        enabled = active, onClick = { open(Page.SearchboxEdge) })
                    ArrowPreference(
                        "安全中心", "工具箱与侧边栏玻璃，保留原生投影",
                        enabled = active, onClick = { open(Page.SecurityCenterEdge) })
                    ArrowPreference(
                        "Dock 原生描边与阴影", "Dock 使用独立 MiShadow 和描边渲染器",
                        enabled = active, onClick = { open(Page.Shadow) })
                }
            }
        }
        if (!global) item(key = "edge-reset") {
            SettingsCard {
                ArrowPreference(
                    title = "恢复继承全局外阴影与描边",
                    summary = "删除本组件全部覆盖参数，不改变其他组件",
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
