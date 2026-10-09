package com.hellovoid.liquiddock

import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hellovoid.liquiddock.config.ConfigSchema
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
internal fun ThirdPartyAppsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    openGboard: () -> Unit,
) {
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            GboardPageHeader(
                "第三方应用适配",
                "为第三方应用提供独立的液态玻璃适配与参数。",
            )
        }
        item { SmallTitle("系统搜索") }
        item {
            GboardSettingsCard {
                ArrowPreference(
                    title = "MIUI 系统搜索",
                    summary = "系统搜索液态玻璃、严格区域采样与背景刷新",
                    enabled = masterEnabled && liquidEnabled,
                    onClick = {
                        context.startActivity(Intent(context, SearchboxSettingsActivity::class.java))
                    },
                )
            }
        }
        item { SmallTitle("输入法") }
        item {
            GboardSettingsCard {
                ArrowPreference(
                    title = "Gboard",
                    summary = "悬浮键盘液态玻璃、独立颜色与模糊度",
                    enabled = masterEnabled && liquidEnabled,
                    onClick = openGboard,
                )
            }
        }
    }
}

@Composable
internal fun GboardSettingsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    onOpenAll: () -> Unit = {},
) {
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    var gboardEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                GboardGlassPreferences.ENABLED_KEY,
                GboardGlassPreferences.ENABLED_DEFAULT,
            ),
        )
    }
    var autoResizeAfterHandleDrag by remember {
        mutableStateOf(
            prefs.getBoolean(
                GboardGlassPreferences.AUTO_RESIZE_AFTER_HANDLE_DRAG_KEY,
                GboardGlassPreferences.AUTO_RESIZE_AFTER_HANDLE_DRAG_DEFAULT,
            ),
        )
    }

    var realtimeBackgroundSampling by remember {
        mutableStateOf(
            prefs.getBoolean(
                GboardGlassPreferences.REALTIME_BACKGROUND_SAMPLING_KEY,
                GboardGlassPreferences.REALTIME_BACKGROUND_SAMPLING_DEFAULT,
            ),
        )
    }

    fun globalBlur(): Float = if (prefs.contains("${ConfigSchema.Glass.BLUR.name()}_tenths")) {
        prefs.getInt("${ConfigSchema.Glass.BLUR.name()}_tenths", 20) / 10f
    } else {
        prefs.getInt(ConfigSchema.Glass.BLUR.name(), ConfigSchema.Glass.BLUR.uiDefault()).toFloat()
    }
    fun globalChannel(key: String, fallback: Int): Float = prefs.getInt(key, fallback).toFloat()

    var blur by remember {
        mutableStateOf(
            if (prefs.contains(GboardGlassPreferences.BLUR_KEY))
                prefs.getInt(GboardGlassPreferences.BLUR_KEY, globalBlur().roundToInt()).toFloat()
            else globalBlur(),
        )
    }
    var tintR by remember {
        mutableStateOf(
            if (prefs.contains(GboardGlassPreferences.TINT_RED_KEY))
                prefs.getInt(GboardGlassPreferences.TINT_RED_KEY, 0).toFloat()
            else globalChannel(ConfigSchema.Glass.TINT_RED.name(), ConfigSchema.Glass.TINT_RED.uiDefault()),
        )
    }
    var tintG by remember {
        mutableStateOf(
            if (prefs.contains(GboardGlassPreferences.TINT_GREEN_KEY))
                prefs.getInt(GboardGlassPreferences.TINT_GREEN_KEY, 0).toFloat()
            else globalChannel(ConfigSchema.Glass.TINT_GREEN.name(), ConfigSchema.Glass.TINT_GREEN.uiDefault()),
        )
    }
    var tintB by remember {
        mutableStateOf(
            if (prefs.contains(GboardGlassPreferences.TINT_BLUE_KEY))
                prefs.getInt(GboardGlassPreferences.TINT_BLUE_KEY, 255).toFloat()
            else globalChannel(ConfigSchema.Glass.TINT_BLUE.name(), ConfigSchema.Glass.TINT_BLUE.uiDefault()),
        )
    }
    var tintAlpha by remember {
        mutableStateOf(
            if (prefs.contains(GboardGlassPreferences.TINT_ALPHA_KEY))
                prefs.getInt(GboardGlassPreferences.TINT_ALPHA_KEY, 35).toFloat()
            else globalChannel(ConfigSchema.Glass.TINT_ALPHA.name(), ConfigSchema.Glass.TINT_ALPHA.uiDefault()),
        )
    }
    var appearanceGeneration by remember { mutableStateOf(0) }
    val controlsEnabled = masterEnabled && liquidEnabled && gboardEnabled
    val hasAppearanceOverride = appearanceGeneration.let {
        prefs.contains(GboardGlassPreferences.BLUR_KEY) ||
            prefs.contains(GboardGlassPreferences.TINT_RED_KEY) ||
            prefs.contains(GboardGlassPreferences.TINT_GREEN_KEY) ||
            prefs.contains(GboardGlassPreferences.TINT_BLUE_KEY) ||
            prefs.contains(GboardGlassPreferences.TINT_ALPHA_KEY)
    }

    fun clearAppearanceOverrides() {
        prefs.edit()
            .remove(GboardGlassPreferences.BLUR_KEY)
            .remove(GboardGlassPreferences.TINT_RED_KEY)
            .remove(GboardGlassPreferences.TINT_GREEN_KEY)
            .remove(GboardGlassPreferences.TINT_BLUE_KEY)
            .remove(GboardGlassPreferences.TINT_ALPHA_KEY)
            .apply()
        blur = globalBlur()
        tintR = globalChannel(ConfigSchema.Glass.TINT_RED.name(), ConfigSchema.Glass.TINT_RED.uiDefault())
        tintG = globalChannel(ConfigSchema.Glass.TINT_GREEN.name(), ConfigSchema.Glass.TINT_GREEN.uiDefault())
        tintB = globalChannel(ConfigSchema.Glass.TINT_BLUE.name(), ConfigSchema.Glass.TINT_BLUE.uiDefault())
        tintAlpha = globalChannel(ConfigSchema.Glass.TINT_ALPHA.name(), ConfigSchema.Glass.TINT_ALPHA.uiDefault())
        appearanceGeneration++
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            GboardPageHeader(
                "Gboard",
                "仅作用于 Gboard 悬浮键盘；颜色与模糊度未单独设置时继承全局液态玻璃。",
            )
        }
        item { SmallTitle("功能") }
        item {
            GboardSettingsCard {
                SwitchPreference(
                    checked = gboardEnabled,
                    onCheckedChange = {
                        gboardEnabled = it
                        prefs.edit().putBoolean(GboardGlassPreferences.ENABLED_KEY, it).apply()
                    },
                    title = "启用悬浮键盘液态玻璃",
                    summary = "关闭后保留 Gboard 原生悬浮键盘材质；已保存的独立外观参数不会丢失",
                    enabled = masterEnabled && liquidEnabled,
                )
                SwitchPreference(
                    checked = autoResizeAfterHandleDrag,
                    onCheckedChange = {
                        autoResizeAfterHandleDrag = it
                        prefs.edit()
                            .putBoolean(GboardGlassPreferences.AUTO_RESIZE_AFTER_HANDLE_DRAG_KEY, it)
                            .apply()
                    },
                    title = "拖动后自动进入大小调整",
                    summary = "关闭后，拖动底部手柄只移动悬浮键盘；仍可通过 Gboard 原生入口手动调整大小",
                    enabled = masterEnabled,
                )
                SwitchPreference(
                    checked = realtimeBackgroundSampling,
                    onCheckedChange = {
                        realtimeBackgroundSampling = it
                        prefs.edit()
                            .putBoolean(GboardGlassPreferences.REALTIME_BACKGROUND_SAMPLING_KEY, it)
                            .apply()
                    },
                    title = "实时背景采样",
                    summary = if (realtimeBackgroundSampling)
                        "持续采样悬浮键盘后方背景"
                    else
                        "冻结模式：弹出动画期间保持实时，动画稳定后冻结；再次移动时刷新背景",
                    enabled = controlsEnabled,
                )
            }
        }
        item { SmallTitle("玻璃颜色") }
        item {
            GboardSettingsCard {
                GboardValueSlider(
                    key = GboardGlassPreferences.TINT_RED_KEY,
                    title = "红",
                    value = tintR,
                    onValueChange = { tintR = it },
                    prefs = prefs,
                    enabled = controlsEnabled,
                    max = 255,
                )
                GboardValueSlider(
                    key = GboardGlassPreferences.TINT_GREEN_KEY,
                    title = "绿",
                    value = tintG,
                    onValueChange = { tintG = it },
                    prefs = prefs,
                    enabled = controlsEnabled,
                    max = 255,
                )
                GboardValueSlider(
                    key = GboardGlassPreferences.TINT_BLUE_KEY,
                    title = "蓝",
                    value = tintB,
                    onValueChange = { tintB = it },
                    prefs = prefs,
                    enabled = controlsEnabled,
                    max = 255,
                )
                GboardValueSlider(
                    key = GboardGlassPreferences.TINT_ALPHA_KEY,
                    title = "不透明度",
                    value = tintAlpha,
                    onValueChange = { tintAlpha = it },
                    prefs = prefs,
                    enabled = controlsEnabled,
                    max = 255,
                )
            }
        }
        item { SmallTitle("模糊") }
        item {
            GboardSettingsCard {
                GboardValueSlider(
                    key = GboardGlassPreferences.BLUR_KEY,
                    title = "玻璃模糊",
                    value = blur,
                    onValueChange = { blur = it },
                    prefs = prefs,
                    enabled = controlsEnabled,
                    max = 60,
                    unit = "px",
                )
                ArrowPreference(
                    title = "恢复继承全局外观",
                    summary = "删除 Gboard 的颜色与模糊度覆盖，重新跟随全局液态玻璃参数",
                    enabled = controlsEnabled && hasAppearanceOverride,
                    onClick = { clearAppearanceOverrides() },
                )
            }
        }
        item { SmallTitle("独立参数") }
        item {
            GboardSettingsCard {
                ArrowPreference(
                    title = "全部参数",
                    summary = "调整 Gboard 专属的完整 Prismal 光学参数；未设置时继承全局",
                    enabled = masterEnabled && liquidEnabled,
                    onClick = onOpenAll,
                )
            }
        }
    }
}

@Composable
internal fun GboardValueSlider(
    key: String,
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    prefs: SharedPreferences,
    enabled: Boolean,
    max: Int,
    unit: String = "",
) {
    val rounded = value.roundToInt().coerceIn(0, max)
    SliderPreference(
        value = rounded.toFloat(),
        onValueChange = {
            val next = it.roundToInt().coerceIn(0, max)
            onValueChange(next.toFloat())
            prefs.edit().putInt(key, next).apply()
        },
        title = title,
        summary = "未单独设置时继承全局液态玻璃",
        valueText = "$rounded${if (unit.isBlank()) "" else " $unit"}",
        enabled = enabled,
        valueRange = 0f..max.toFloat(),
        steps = (max - 1).coerceAtLeast(0),
        insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 2.dp),
    )
}

@Composable
internal fun GboardPageHeader(title: String, summary: String) {
    PageHeader(title, summary)
}


@Composable
internal fun GboardSettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    SettingsCard(content)
}
