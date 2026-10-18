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
) {
    val global = scope == GlassEdgeEffects.GLOBAL
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
        else -> scope
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item(key = "edge-intro") {
            PageHeader("外阴影与描边",
                "$scopeLabel · 外部阴影与真实描边独立控制。阴影方向跟随 Prismal 光源，" +
                (if (global) "其他组件可继承此配置。" else "未覆盖时继承全局默认。"))
        }
        item(key = "shadow-label") { SmallTitle("外阴影") }
        item(key = "shadow-controls") {
            SettingsCard {
                toggle(ConfigSchema.Glass.OUTER_SHADOW_ENABLED, "启用外阴影",
                    "独立于玻璃内部阴影")
                number(ConfigSchema.Glass.OUTER_SHADOW_RADIUS, "柔和半径", "dp")
                number(ConfigSchema.Glass.OUTER_SHADOW_OFFSET_Y, "垂直偏移", "dp")
                number(ConfigSchema.Glass.OUTER_SHADOW_ALPHA, "阴影不透明度")
                number(ConfigSchema.Glass.OUTER_SHADOW_RED, "阴影红")
                number(ConfigSchema.Glass.OUTER_SHADOW_GREEN, "阴影绿")
                number(ConfigSchema.Glass.OUTER_SHADOW_BLUE, "阴影蓝")
                number(ConfigSchema.Glass.OUTER_SHADOW_LIGHT_FOLLOW, "光照方向跟随", "%")
            }
        }
        item(key = "stroke-label") { SmallTitle("描边") }
        item(key = "stroke-controls") {
            SettingsCard {
                toggle(ConfigSchema.Glass.OUTER_STROKE_ENABLED, "启用独立描边",
                    "叠加于 Prismal 的自然光学边缘")
                number(ConfigSchema.Glass.OUTER_STROKE_WIDTH, "描边宽度", "dp")
                number(ConfigSchema.Glass.OUTER_STROKE_ALPHA, "描边不透明度")
                number(ConfigSchema.Glass.OUTER_STROKE_RED, "描边红")
                number(ConfigSchema.Glass.OUTER_STROKE_GREEN, "描边绿")
                number(ConfigSchema.Glass.OUTER_STROKE_BLUE, "描边蓝")
                number(ConfigSchema.Glass.OUTER_STROKE_LIGHT_RESPONSE, "描边光照响应", "%")
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
