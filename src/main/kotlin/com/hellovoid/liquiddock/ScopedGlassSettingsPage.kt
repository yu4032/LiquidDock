package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import com.hellovoid.liquiddock.config.ConfigKey
import com.hellovoid.liquiddock.config.ConfigSchema
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.SmallTitle

/**
 * Each child scope owns its own page and its own configuration namespace.
 * This composable only reuses the slider presentation, never profile state or keys.
 */
@Composable
internal fun ScopedGlassSettingsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    scope: String,
    ownerName: String,
) {
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(), ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    val enabled = masterEnabled && liquidEnabled
    var resetGeneration by remember(scope) { mutableIntStateOf(0) }
    val basics = remember(scope) { basicScopeSpecs(scope) }
    val optics = scopedOpticalDescriptors
    val normalsKey = ScopedGlassOptics.normalsKey(scope)
    val extras = when (scope) {
        ScopedGlassOptics.DIALOG -> emptyList()
        ScopedGlassOptics.GBOARD -> listOf("capture_scale_percent", "render_fps")
        else -> listOf("capture_scale_percent", "render_fps", "corner_radius_dp")
    }
    // These are separate rounded cells, not rows inside one shared card.
    // Keep a visible gap without changing each cell's surface or interaction.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "scope-header") {
            PageHeader(
                "全部参数",
                "$ownerName 的参数独立保存；未手动设置的参数实时继承全局液态玻璃。修改不会覆盖其他应用或主界面玻璃。",
            )
        }
        item(key = "scope-reset") {
            SettingsCard {
                ArrowPreference(
                    title = "恢复全部参数继承",
                    summary = "只删除$ownerName 的独立参数；不影响全局配置和其他应用",
                    enabled = enabled,
                    onClick = {
                        val edit = prefs.edit()
                        for (b in basics) edit.remove(b.key)
                        for (d in optics) {
                            val key = ScopedGlassOptics.key(scope, d.config)
                            edit.remove(key).remove("${key}_tenths")
                        }
                        edit.remove(normalsKey)
                        for (name in extras) {
                            edit.remove(profileOptionKey(scope, name))
                        }
                        if (scope != ScopedGlassOptics.DIALOG) {
                            for (setting in profileHighlightSpecs) {
                                edit.remove(profileOptionKey(scope, setting.field))
                            }
                            if (scope == ScopedGlassOptics.SEARCHBOX) {
                                edit.remove(profileOptionKey(scope, "fresh_on_resume"))
                            }
                        }
                        edit.apply()
                        resetGeneration++
                    },
                )
            }
        }
        item(key = "basic-heading") { SmallTitle("颜色与模糊") }
        basics.forEach { b ->
            item(key = b.key) {
                SettingsCard {
                    ScopedGlassSlider(
                        prefs = prefs,
                        key = b.key,
                        fallback = b.fallback,
                        title = b.title,
                        unit = b.unit,
                        enabled = enabled,
                        generation = resetGeneration,
                        decimal = false,
                    )
                }
            }
        }
        item(key = "optical-heading") { SmallTitle("光学、折射、高光及阴影") }
        optics.forEach { d ->
            item(key = d.config.name()) {
                SettingsCard {
                    val key = ScopedGlassOptics.key(scope, d.config)
                    ScopedGlassSlider(
                        prefs = prefs,
                        key = key,
                        fallback = d.config,
                        title = d.title,
                        unit = d.unit,
                        enabled = enabled,
                        generation = resetGeneration,
                        decimal = d.config.storageMode() == ConfigKey.StorageMode.DP_TENTHS,
                    )
                }
            }
        }
        item(key = "normals") {
            SettingsCard {
                val inherited = prefs.getBoolean(
                    ConfigSchema.Glass.PRISMAL_SHOW_NORMALS.name(),
                    ConfigSchema.Glass.PRISMAL_SHOW_NORMALS.uiDefault(),
                )
                var checked by remember(scope, resetGeneration) {
                    mutableStateOf(prefs.getBoolean(normalsKey, inherited))
                }
                SwitchPreference(
                    title = "显示法线调试",
                    summary = if (prefs.contains(normalsKey)) "独立设置" else "继承全局",
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = {
                        checked = it
                        prefs.edit().putBoolean(normalsKey, it).apply()
                    },
                )
            }
        }
        if (scope != ScopedGlassOptics.DIALOG) {
            item(key = "quality-heading") { SmallTitle("背景采样与轮廓") }
            for (field in extras) {
                val config = when (field) {
                    "capture_scale_percent" -> ConfigSchema.Glass.PASSBLUR_CAPTURE_SCALE
                    "render_fps" -> ConfigSchema.Glass.PASSBLUR_RENDER_FPS
                    else -> null
                }
                item(key = "quality-$field") {
                    SettingsCard {
                        if (config != null) {
                            ScopedGlassSlider(
                                prefs, profileOptionKey(scope, field), config,
                                when (field) {
                                    "capture_scale_percent" -> "采样比例"
                                    else -> "渲染帧率上限"
                                },
                                if (field == "capture_scale_percent") "%" else "Hz",
                                enabled, resetGeneration, decimal = false,
                            )
                        } else {
                            val key = profileOptionKey(scope, field)
                            var v by remember(key, resetGeneration) {
                                // Imported profiles may store this documented FLOAT field.
                                mutableStateOf((prefs.all[key] as? Number)?.toFloat() ?: -1f)
                            }
                            SliderPreference(
                                value = v,
                                onValueChange = {
                                    val next = it.roundToInt().coerceIn(-1, 400)
                                    v = next.toFloat()
                                    prefs.edit().putFloat(key, next.toFloat()).apply()
                                },
                                title = "独立圆角半径",
                                summary = if (prefs.contains(key)) "−1 表示继承自动圆角"
                                    else "继承自动圆角",
                                valueText = "${v.roundToInt()} dp",
                                enabled = enabled,
                                valueRange = -1f..400f,
                                steps = 400,
                            )
                        }
                    }
                }
            }
            item(key = "profile-highlights-heading") { SmallTitle("独立高光开关") }
            for (setting in profileHighlightSpecs) {
                item(key = "highlight-${setting.field}") {
                    SettingsCard {
                        val key = profileOptionKey(scope, setting.field)
                        val inherited = prefs.getBoolean(
                            setting.global.name(), setting.global.uiDefault(),
                        )
                        var checked by remember(key, resetGeneration) {
                            mutableStateOf(prefs.getBoolean(key, inherited))
                        }
                        SwitchPreference(
                            checked = checked,
                            onCheckedChange = {
                                checked = it
                                prefs.edit().putBoolean(key, it).apply()
                            },
                            title = setting.title,
                            summary = if (prefs.contains(key)) "独立设置" else "继承全局高光配置",
                            enabled = enabled,
                        )
                    }
                }
            }
            if (scope == ScopedGlassOptics.SEARCHBOX) {
                item(key = "search-refresh") {
                    SettingsCard {
                        val key = profileOptionKey(scope, "fresh_on_resume")
                        var checked by remember(key, resetGeneration) {
                            mutableStateOf(prefs.getBoolean(key, true))
                        }
                        SwitchPreference(
                            checked = checked,
                            onCheckedChange = {
                                checked = it
                                prefs.edit().putBoolean(key, it).apply()
                            },
                            title = "重新进入搜索时刷新背景",
                            summary = if (prefs.contains(key)) "独立设置" else "继承搜索默认策略",
                            enabled = enabled,
                        )
                    }
                }
            }
        }
    }
}

private data class ProfileHighlightSpec(
    val field: String,
    val title: String,
    val global: ConfigKey<Boolean>,
)

private val profileHighlightSpecs = listOf(
    ProfileHighlightSpec("highlight_sky_haze", "天空泛光", ConfigSchema.LauncherHighlight.LARGE_SKY_HAZE),
    ProfileHighlightSpec("highlight_specular", "镜面高光", ConfigSchema.LauncherHighlight.LARGE_SPECULAR),
    ProfileHighlightSpec("highlight_lit_rim", "受光边缘", ConfigSchema.LauncherHighlight.LARGE_LIT_RIM),
    ProfileHighlightSpec("highlight_opposite_rim", "背光边缘", ConfigSchema.LauncherHighlight.LARGE_OPPOSITE_RIM),
    ProfileHighlightSpec("highlight_corner_rim", "圆角边缘", ConfigSchema.LauncherHighlight.LARGE_CORNER_RIM),
    ProfileHighlightSpec("highlight_face_sheen", "表面柔光", ConfigSchema.LauncherHighlight.LARGE_FACE_SHEEN),
    ProfileHighlightSpec("highlight_plain", "基础高光", ConfigSchema.LauncherHighlight.LARGE_PLAIN_HIGHLIGHT),
    ProfileHighlightSpec("highlight_caustics", "焦散高光", ConfigSchema.LauncherHighlight.LARGE_CAUSTICS),
    ProfileHighlightSpec("highlight_press_glow", "按压泛光", ConfigSchema.LauncherHighlight.LARGE_PRESS_GLOW),
)

private data class BasicScopeSpec(
    val key: String,
    val title: String,
    val fallback: ConfigKey<Int>,
    val unit: String,
)

private fun basicScopeSpecs(scope: String): List<BasicScopeSpec> {
    val names = when (scope) {
        ScopedGlassOptics.GBOARD -> listOf(
            GboardGlassPreferences.BLUR_KEY,
            GboardGlassPreferences.TINT_RED_KEY,
            GboardGlassPreferences.TINT_GREEN_KEY,
            GboardGlassPreferences.TINT_BLUE_KEY,
            GboardGlassPreferences.TINT_ALPHA_KEY,
        )
        ScopedGlassOptics.SEARCHBOX -> listOf(
            MiuiSearchboxGlassPreferences.BLUR_KEY,
            MiuiSearchboxGlassPreferences.TINT_RED_KEY,
            MiuiSearchboxGlassPreferences.TINT_GREEN_KEY,
            MiuiSearchboxGlassPreferences.TINT_BLUE_KEY,
            MiuiSearchboxGlassPreferences.TINT_ALPHA_KEY,
        )
        ScopedGlassOptics.DIALOG -> listOf(
            ConfigSchema.Glass.DIALOG_BLUR.name(),
            ConfigSchema.Glass.DIALOG_TINT_RED.name(),
            ConfigSchema.Glass.DIALOG_TINT_GREEN.name(),
            ConfigSchema.Glass.DIALOG_TINT_BLUE.name(),
            ConfigSchema.Glass.DIALOG_TINT_ALPHA.name(),
        )
        else -> throw IllegalArgumentException("Unsupported glass scope $scope")
    }
    return names.zip(
        listOf(
            Triple("玻璃模糊", ConfigSchema.Glass.BLUR, "px"),
            Triple("底色 · 红", ConfigSchema.Glass.TINT_RED, ""),
            Triple("底色 · 绿", ConfigSchema.Glass.TINT_GREEN, ""),
            Triple("底色 · 蓝", ConfigSchema.Glass.TINT_BLUE, ""),
            Triple("底色 · 透明度", ConfigSchema.Glass.TINT_ALPHA, ""),
        )
    ) { key, (name, config, unit) -> BasicScopeSpec(key, name, config, unit) }
}

private fun profileOptionKey(scope: String, field: String): String {
    val profileId = when (scope) {
        ScopedGlassOptics.GBOARD -> GboardGlassPreferences.PROFILE_ID
        ScopedGlassOptics.SEARCHBOX -> MiuiSearchboxGlassPreferences.PROFILE_ID
        else -> throw IllegalArgumentException("No third-party profile for $scope")
    }
    return ThirdPartyGlassProfiles.key(profileId, field)
}

@Composable
private fun ScopedGlassSlider(
    prefs: SharedPreferences,
    key: String,
    fallback: ConfigKey<Int>,
    title: String,
    unit: String,
    enabled: Boolean,
    generation: Int,
    decimal: Boolean,
) {
    fun inherited(): Float {
        val global = fallback.name()
        return if (fallback.storageMode() == ConfigKey.StorageMode.DP_TENTHS
            && prefs.contains("${global}_tenths")) {
            prefs.getInt("${global}_tenths", fallback.uiDefault() * 10) / 10f
        } else {
            prefs.getInt(global, fallback.uiDefault()).toFloat()
        }
    }

    fun read(): Float = if (decimal && prefs.contains("${key}_tenths")) {
        prefs.getInt("${key}_tenths", fallback.uiDefault() * 10) / 10f
    } else if (prefs.contains(key)) {
        prefs.getInt(key, fallback.uiDefault()).toFloat()
    } else inherited()

    var v by remember(key, generation) { mutableStateOf(read()) }
    val overridden = prefs.contains(key) || prefs.contains("${key}_tenths")
    val min = fallback.minInt().toFloat()
    val max = fallback.maxInt().toFloat()
    val multiplier = if (decimal) 10 else 1
    val intervals = ((max - min) * multiplier).roundToInt().coerceAtLeast(1)
    SliderPreference(
        value = v.coerceIn(min, max),
        onValueChange = { raw ->
            val next = if (decimal) (raw * 10f).roundToInt() / 10f
                else raw.roundToInt().toFloat()
            val bounded = next.coerceIn(min, max)
            v = bounded
            val editor = prefs.edit().putInt(key, bounded.roundToInt())
            if (decimal) editor.putInt("${key}_tenths", (bounded * 10f).roundToInt())
            editor.apply()
        },
        title = title,
        summary = if (overridden) "独立设置 · $title" else "继承全局液态玻璃",
        valueText = if (decimal) String.format(java.util.Locale.ROOT, "%.1f", v) +
            (if (unit.isBlank()) "" else " $unit")
            else "${v.roundToInt()}" + (if (unit.isBlank()) "" else " $unit"),
        enabled = enabled,
        valueRange = min..max,
        steps = (intervals - 1).coerceAtLeast(0),
    )
}
