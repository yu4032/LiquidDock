package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hellovoid.liquiddock.config.ConfigSchema
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
internal fun SearchboxSettingsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    onOpenAll: () -> Unit = {},
) {
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    var searchboxEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                MiuiSearchboxGlassPreferences.ENABLED_KEY,
                MiuiSearchboxGlassPreferences.ENABLED_DEFAULT,
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
            if (prefs.contains(MiuiSearchboxGlassPreferences.BLUR_KEY))
                prefs.getInt(MiuiSearchboxGlassPreferences.BLUR_KEY, globalBlur().roundToInt()).toFloat()
            else globalBlur(),
        )
    }
    var tintR by remember {
        mutableStateOf(
            if (prefs.contains(MiuiSearchboxGlassPreferences.TINT_RED_KEY))
                prefs.getInt(MiuiSearchboxGlassPreferences.TINT_RED_KEY, 0).toFloat()
            else globalChannel(ConfigSchema.Glass.TINT_RED.name(), ConfigSchema.Glass.TINT_RED.uiDefault()),
        )
    }
    var tintG by remember {
        mutableStateOf(
            if (prefs.contains(MiuiSearchboxGlassPreferences.TINT_GREEN_KEY))
                prefs.getInt(MiuiSearchboxGlassPreferences.TINT_GREEN_KEY, 0).toFloat()
            else globalChannel(ConfigSchema.Glass.TINT_GREEN.name(), ConfigSchema.Glass.TINT_GREEN.uiDefault()),
        )
    }
    var tintB by remember {
        mutableStateOf(
            if (prefs.contains(MiuiSearchboxGlassPreferences.TINT_BLUE_KEY))
                prefs.getInt(MiuiSearchboxGlassPreferences.TINT_BLUE_KEY, 255).toFloat()
            else globalChannel(ConfigSchema.Glass.TINT_BLUE.name(), ConfigSchema.Glass.TINT_BLUE.uiDefault()),
        )
    }
    var tintAlpha by remember {
        mutableStateOf(
            if (prefs.contains(MiuiSearchboxGlassPreferences.TINT_ALPHA_KEY))
                prefs.getInt(MiuiSearchboxGlassPreferences.TINT_ALPHA_KEY, 35).toFloat()
            else globalChannel(ConfigSchema.Glass.TINT_ALPHA.name(), ConfigSchema.Glass.TINT_ALPHA.uiDefault()),
        )
    }
    var appearanceGeneration by remember { mutableStateOf(0) }
    val controlsEnabled = masterEnabled && liquidEnabled && searchboxEnabled
    val hasAppearanceOverride = appearanceGeneration.let {
        prefs.contains(MiuiSearchboxGlassPreferences.BLUR_KEY) ||
            prefs.contains(MiuiSearchboxGlassPreferences.TINT_RED_KEY) ||
            prefs.contains(MiuiSearchboxGlassPreferences.TINT_GREEN_KEY) ||
            prefs.contains(MiuiSearchboxGlassPreferences.TINT_BLUE_KEY) ||
            prefs.contains(MiuiSearchboxGlassPreferences.TINT_ALPHA_KEY)
    }

    fun clearAppearanceOverrides() {
        prefs.edit()
            .remove(MiuiSearchboxGlassPreferences.BLUR_KEY)
            .remove(MiuiSearchboxGlassPreferences.TINT_RED_KEY)
            .remove(MiuiSearchboxGlassPreferences.TINT_GREEN_KEY)
            .remove(MiuiSearchboxGlassPreferences.TINT_BLUE_KEY)
            .remove(MiuiSearchboxGlassPreferences.TINT_ALPHA_KEY)
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
                "系统搜索",
                "仅替换 MIUI 搜索主界面的原生 blur 背景；搜索框自身背景保持原样。颜色与模糊度未单独设置时继承全局液态玻璃。",
            )
        }
        item { SmallTitle("功能") }
        item {
            GboardSettingsCard {
                SwitchPreference(
                    checked = searchboxEnabled,
                    onCheckedChange = {
                        searchboxEnabled = it
                        prefs.edit()
                            .putBoolean(MiuiSearchboxGlassPreferences.ENABLED_KEY, it)
                            .apply()
                    },
                    title = "MIUI 搜索主界面液态玻璃",
                    summary = "玻璃采样严格对应背景 View 后方区域；重新调出搜索时强制刷新当前帧",
                    enabled = masterEnabled && liquidEnabled,
                )
            }
        }
        item { SmallTitle("玻璃颜色") }
        item {
            GboardSettingsCard {
                GboardValueSlider(
                    key = MiuiSearchboxGlassPreferences.TINT_RED_KEY,
                    title = "红",
                    value = tintR,
                    onValueChange = { tintR = it },
                    prefs = prefs,
                    enabled = controlsEnabled,
                    max = 255,
                )
                GboardValueSlider(
                    key = MiuiSearchboxGlassPreferences.TINT_GREEN_KEY,
                    title = "绿",
                    value = tintG,
                    onValueChange = { tintG = it },
                    prefs = prefs,
                    enabled = controlsEnabled,
                    max = 255,
                )
                GboardValueSlider(
                    key = MiuiSearchboxGlassPreferences.TINT_BLUE_KEY,
                    title = "蓝",
                    value = tintB,
                    onValueChange = { tintB = it },
                    prefs = prefs,
                    enabled = controlsEnabled,
                    max = 255,
                )
                GboardValueSlider(
                    key = MiuiSearchboxGlassPreferences.TINT_ALPHA_KEY,
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
                    key = MiuiSearchboxGlassPreferences.BLUR_KEY,
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
                    summary = "删除系统搜索的颜色与模糊度覆盖，重新跟随全局液态玻璃参数",
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
                    summary = "调整系统搜索专属的完整 Prismal 光学参数；未设置时继承全局",
                    enabled = masterEnabled && liquidEnabled,
                    onClick = onOpenAll,
                )
            }
        }
    }
}
