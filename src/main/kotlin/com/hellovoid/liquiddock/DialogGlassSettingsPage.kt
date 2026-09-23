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
import androidx.compose.ui.res.stringResource
import com.hellovoid.liquiddock.config.ConfigSchema
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference

/** Launcher-owned dialog glass controls. Appearance overrides inherit the global material by default. */
@Composable
internal fun DialogGlassSettingsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    var dialogEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                ConfigSchema.Glass.UNINSTALL_DIALOG_GLASS.name(),
                ConfigSchema.Glass.UNINSTALL_DIALOG_GLASS.uiDefault(),
            ),
        )
    }

    fun globalBlur(): Float =
        if (prefs.contains("${ConfigSchema.Glass.BLUR.name()}_tenths")) {
            prefs.getInt(
                "${ConfigSchema.Glass.BLUR.name()}_tenths",
                ConfigSchema.Glass.BLUR.uiDefault() * 10,
            ) / 10f
        } else {
            prefs.getInt(
                ConfigSchema.Glass.BLUR.name(),
                ConfigSchema.Glass.BLUR.uiDefault(),
            ).toFloat()
        }

    fun globalChannel(key: String, fallback: Int): Float =
        prefs.getInt(key, fallback).toFloat()

    var blur by remember {
        mutableStateOf(
            if (prefs.contains(ConfigSchema.Glass.DIALOG_BLUR.name())) {
                prefs.getInt(
                    ConfigSchema.Glass.DIALOG_BLUR.name(),
                    globalBlur().roundToInt(),
                ).toFloat()
            } else {
                globalBlur()
            },
        )
    }
    var tintR by remember {
        mutableStateOf(
            if (prefs.contains(ConfigSchema.Glass.DIALOG_TINT_RED.name())) {
                prefs.getInt(ConfigSchema.Glass.DIALOG_TINT_RED.name(), 0).toFloat()
            } else {
                globalChannel(
                    ConfigSchema.Glass.TINT_RED.name(),
                    ConfigSchema.Glass.TINT_RED.uiDefault(),
                )
            },
        )
    }
    var tintG by remember {
        mutableStateOf(
            if (prefs.contains(ConfigSchema.Glass.DIALOG_TINT_GREEN.name())) {
                prefs.getInt(ConfigSchema.Glass.DIALOG_TINT_GREEN.name(), 0).toFloat()
            } else {
                globalChannel(
                    ConfigSchema.Glass.TINT_GREEN.name(),
                    ConfigSchema.Glass.TINT_GREEN.uiDefault(),
                )
            },
        )
    }
    var tintB by remember {
        mutableStateOf(
            if (prefs.contains(ConfigSchema.Glass.DIALOG_TINT_BLUE.name())) {
                prefs.getInt(ConfigSchema.Glass.DIALOG_TINT_BLUE.name(), 255).toFloat()
            } else {
                globalChannel(
                    ConfigSchema.Glass.TINT_BLUE.name(),
                    ConfigSchema.Glass.TINT_BLUE.uiDefault(),
                )
            },
        )
    }
    var tintAlpha by remember {
        mutableStateOf(
            if (prefs.contains(ConfigSchema.Glass.DIALOG_TINT_ALPHA.name())) {
                prefs.getInt(ConfigSchema.Glass.DIALOG_TINT_ALPHA.name(), 35).toFloat()
            } else {
                globalChannel(
                    ConfigSchema.Glass.TINT_ALPHA.name(),
                    ConfigSchema.Glass.TINT_ALPHA.uiDefault(),
                )
            },
        )
    }
    var appearanceGeneration by remember { mutableStateOf(0) }
    val controlsEnabled = masterEnabled && liquidEnabled && dialogEnabled
    val hasAppearanceOverride = appearanceGeneration.let {
        prefs.contains(ConfigSchema.Glass.DIALOG_BLUR.name()) ||
            prefs.contains(ConfigSchema.Glass.DIALOG_TINT_RED.name()) ||
            prefs.contains(ConfigSchema.Glass.DIALOG_TINT_GREEN.name()) ||
            prefs.contains(ConfigSchema.Glass.DIALOG_TINT_BLUE.name()) ||
            prefs.contains(ConfigSchema.Glass.DIALOG_TINT_ALPHA.name())
    }

    fun clearAppearanceOverrides() {
        prefs.edit()
            .remove(ConfigSchema.Glass.DIALOG_BLUR.name())
            .remove(ConfigSchema.Glass.DIALOG_TINT_RED.name())
            .remove(ConfigSchema.Glass.DIALOG_TINT_GREEN.name())
            .remove(ConfigSchema.Glass.DIALOG_TINT_BLUE.name())
            .remove(ConfigSchema.Glass.DIALOG_TINT_ALPHA.name())
            .apply()
        blur = globalBlur()
        tintR = globalChannel(
            ConfigSchema.Glass.TINT_RED.name(),
            ConfigSchema.Glass.TINT_RED.uiDefault(),
        )
        tintG = globalChannel(
            ConfigSchema.Glass.TINT_GREEN.name(),
            ConfigSchema.Glass.TINT_GREEN.uiDefault(),
        )
        tintB = globalChannel(
            ConfigSchema.Glass.TINT_BLUE.name(),
            ConfigSchema.Glass.TINT_BLUE.uiDefault(),
        )
        tintAlpha = globalChannel(
            ConfigSchema.Glass.TINT_ALPHA.name(),
            ConfigSchema.Glass.TINT_ALPHA.uiDefault(),
        )
        appearanceGeneration++
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            PageHeader(
                stringResource(R.string.page_dialog_customization),
                "当前作用于桌面卸载、移除和二次确认弹窗；未单独设置的颜色与模糊度继承全局液态玻璃。",
            )
        }
        item { SmallTitle("功能") }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.UNINSTALL_DIALOG_GLASS,
                    "启用桌面对话弹窗玻璃",
                    "替换桌面卸载、移除与二次确认弹窗背景；失败时立即恢复系统原生材质",
                    masterEnabled && liquidEnabled,
                ) { dialogEnabled = it }
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.DIALOG_DISABLE_DIMMING,
                    "关闭对话时背景压暗",
                    "只隐藏压暗效果，仍保留点击对话框外部关闭弹窗的行为",
                    controlsEnabled,
                )
                BooleanSetting(
                    prefs,
                    ConfigSchema.Glass.DIALOG_DARK_MODE,
                    "对话框深色模式",
                    "只适配文字与按钮可读性；图标和玻璃颜色保持原样，继续使用你的对话玻璃自定义",
                    controlsEnabled,
                )
            }
        }
        item { SmallTitle("玻璃颜色") }
        item {
            SettingsCard {
                GlassAppearanceValueSlider(
                    key = ConfigSchema.Glass.DIALOG_TINT_RED.name(),
                    title = "红",
                    value = tintR,
                    onValueChange = { tintR = it },
                    prefs = prefs,
                    enabled = controlsEnabled,
                    max = 255,
                )
                GlassAppearanceValueSlider(
                    key = ConfigSchema.Glass.DIALOG_TINT_GREEN.name(),
                    title = "绿",
                    value = tintG,
                    onValueChange = { tintG = it },
                    prefs = prefs,
                    enabled = controlsEnabled,
                    max = 255,
                )
                GlassAppearanceValueSlider(
                    key = ConfigSchema.Glass.DIALOG_TINT_BLUE.name(),
                    title = "蓝",
                    value = tintB,
                    onValueChange = { tintB = it },
                    prefs = prefs,
                    enabled = controlsEnabled,
                    max = 255,
                )
                GlassAppearanceValueSlider(
                    key = ConfigSchema.Glass.DIALOG_TINT_ALPHA.name(),
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
            SettingsCard {
                GlassAppearanceValueSlider(
                    key = ConfigSchema.Glass.DIALOG_BLUR.name(),
                    title = "对话背景模糊",
                    value = blur,
                    onValueChange = { blur = it },
                    prefs = prefs,
                    enabled = controlsEnabled,
                    max = 60,
                    unit = "px",
                )
                ArrowPreference(
                    title = "恢复继承全局外观",
                    summary = "删除对话弹窗的颜色与模糊度覆盖，重新跟随全局液态玻璃参数",
                    enabled = controlsEnabled && hasAppearanceOverride,
                    onClick = { clearAppearanceOverrides() },
                )
            }
        }
    }
}
