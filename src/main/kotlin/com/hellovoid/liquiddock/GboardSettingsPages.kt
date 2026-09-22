package com.hellovoid.liquiddock

import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.sp
import com.hellovoid.liquiddock.config.ConfigSchema
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun ThirdPartyAppsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    openGboard: () -> Unit,
    openLockScreenClock: () -> Unit,
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
                    summary = "主界面液态玻璃、严格区域采样与搜索进程控制",
                    enabled = masterEnabled && liquidEnabled,
                    onClick = {
                        context.startActivity(Intent(context, SearchboxSettingsActivity::class.java))
                    },
                )
            }
        }
        item { SmallTitle("系统界面") }
        item {
            GboardSettingsCard {
                ArrowPreference(
                    title = "锁屏时钟",
                    summary = "强制用 LiquidDock 液态玻璃替换 OS3 锁屏时钟；不依赖原厂 Glass 支持",
                    enabled = masterEnabled && liquidEnabled,
                    onClick = openLockScreenClock,
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
internal fun LockScreenClockSettingsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val liquidEnabled = prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(),
        ConfigSchema.Glass.ENABLED.uiDefault(),
    )
    var enabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                LockScreenClockGlassPreferences.ENABLED_KEY,
                LockScreenClockGlassPreferences.ENABLED_DEFAULT,
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
            if (prefs.contains(LockScreenClockGlassPreferences.BLUR_KEY))
                prefs.getFloat(LockScreenClockGlassPreferences.BLUR_KEY, globalBlur())
            else globalBlur(),
        )
    }
    var tintR by remember {
        mutableStateOf(
            if (prefs.contains(LockScreenClockGlassPreferences.TINT_RED_KEY))
                prefs.getInt(LockScreenClockGlassPreferences.TINT_RED_KEY, 0).toFloat()
            else globalChannel(ConfigSchema.Glass.TINT_RED.name(), ConfigSchema.Glass.TINT_RED.uiDefault()),
        )
    }
    var tintG by remember {
        mutableStateOf(
            if (prefs.contains(LockScreenClockGlassPreferences.TINT_GREEN_KEY))
                prefs.getInt(LockScreenClockGlassPreferences.TINT_GREEN_KEY, 0).toFloat()
            else globalChannel(ConfigSchema.Glass.TINT_GREEN.name(), ConfigSchema.Glass.TINT_GREEN.uiDefault()),
        )
    }
    var tintB by remember {
        mutableStateOf(
            if (prefs.contains(LockScreenClockGlassPreferences.TINT_BLUE_KEY))
                prefs.getInt(LockScreenClockGlassPreferences.TINT_BLUE_KEY, 255).toFloat()
            else globalChannel(ConfigSchema.Glass.TINT_BLUE.name(), ConfigSchema.Glass.TINT_BLUE.uiDefault()),
        )
    }
    var tintAlpha by remember {
        mutableStateOf(
            if (prefs.contains(LockScreenClockGlassPreferences.TINT_ALPHA_KEY))
                prefs.getInt(LockScreenClockGlassPreferences.TINT_ALPHA_KEY, 35).toFloat()
            else globalChannel(ConfigSchema.Glass.TINT_ALPHA.name(), ConfigSchema.Glass.TINT_ALPHA.uiDefault()),
        )
    }
    var appearanceGeneration by remember { mutableStateOf(0) }
    val controlsEnabled = masterEnabled && liquidEnabled && enabled
    val hasAppearanceOverride = appearanceGeneration.let {
        prefs.contains(LockScreenClockGlassPreferences.BLUR_KEY) ||
            prefs.contains(LockScreenClockGlassPreferences.TINT_RED_KEY) ||
            prefs.contains(LockScreenClockGlassPreferences.TINT_GREEN_KEY) ||
            prefs.contains(LockScreenClockGlassPreferences.TINT_BLUE_KEY) ||
            prefs.contains(LockScreenClockGlassPreferences.TINT_ALPHA_KEY)
    }

    fun clearAppearanceOverrides() {
        prefs.edit()
            .remove(LockScreenClockGlassPreferences.BLUR_KEY)
            .remove(LockScreenClockGlassPreferences.TINT_RED_KEY)
            .remove(LockScreenClockGlassPreferences.TINT_GREEN_KEY)
            .remove(LockScreenClockGlassPreferences.TINT_BLUE_KEY)
            .remove(LockScreenClockGlassPreferences.TINT_ALPHA_KEY)
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
                "锁屏时钟",
                "OS3 强制替换模式：忽略系统与锁屏的 Glass 支持判断，SystemUI 创建时钟后由 LiquidDock 直接接管。修改后建议重启系统界面。",
            )
        }
        item { SmallTitle("功能") }
        item {
            GboardSettingsCard {
                SwitchPreference(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        prefs.edit()
                            .putBoolean(LockScreenClockGlassPreferences.ENABLED_KEY, it)
                            .apply()
                    },
                    title = "锁屏时钟液态玻璃",
                    summary = "关闭时保留 OS3 原生时钟；开启后无条件尝试用 LiquidDock Prismal 替换",
                    enabled = masterEnabled && liquidEnabled,
                )
            }
        }
        item { SmallTitle("自定义") }
        item {
            GboardSettingsCard {
                GboardValueSlider(
                    key = LockScreenClockGlassPreferences.TINT_RED_KEY,
                    title = "红",
                    value = tintR,
                    onValueChange = { tintR = it },
                    prefs = prefs,
                    enabled = controlsEnabled,
                    max = 255,
                )
                GboardValueSlider(
                    key = LockScreenClockGlassPreferences.TINT_GREEN_KEY,
                    title = "绿",
                    value = tintG,
                    onValueChange = { tintG = it },
                    prefs = prefs,
                    enabled = controlsEnabled,
                    max = 255,
                )
                GboardValueSlider(
                    key = LockScreenClockGlassPreferences.TINT_BLUE_KEY,
                    title = "蓝",
                    value = tintB,
                    onValueChange = { tintB = it },
                    prefs = prefs,
                    enabled = controlsEnabled,
                    max = 255,
                )
                GboardValueSlider(
                    key = LockScreenClockGlassPreferences.TINT_ALPHA_KEY,
                    title = "不透明度",
                    value = tintAlpha,
                    onValueChange = { tintAlpha = it },
                    prefs = prefs,
                    enabled = controlsEnabled,
                    max = 255,
                )
                SliderPreference(
                    value = blur,
                    onValueChange = {
                        val next = it.coerceIn(0f, 60f)
                        blur = next
                        prefs.edit()
                            .putFloat(LockScreenClockGlassPreferences.BLUR_KEY, next)
                            .apply()
                    },
                    title = "玻璃模糊",
                    summary = "未单独设置时继承全局液态玻璃",
                    valueText = String.format(java.util.Locale.ROOT, "%.1f px", blur),
                    enabled = controlsEnabled,
                    valueRange = 0f..60f,
                    steps = 0,
                )
                ArrowPreference(
                    title = "恢复继承全局外观",
                    summary = "删除锁屏时钟的颜色与模糊覆盖，重新跟随全局液态玻璃参数",
                    enabled = controlsEnabled && hasAppearanceOverride,
                    onClick = { clearAppearanceOverrides() },
                )
            }
        }
    }
}

@Composable
internal fun SearchboxSettingsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
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
    }
}

@Composable
internal fun GboardSettingsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
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
    }
}

@Composable
private fun GboardValueSlider(
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
        valueText = "",
        enabled = enabled,
        valueRange = 0f..max.toFloat(),
        steps = (max - 1).coerceAtLeast(0),
        endActions = {
            Button(
                onClick = {},
                enabled = false,
                minWidth = 62.dp,
                minHeight = 32.dp,
                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("$rounded${if (unit.isBlank()) "" else " $unit"}")
            }
        },
        insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 2.dp),
    )
}

@Composable
private fun GboardPageHeader(title: String, summary: String) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(title, fontSize = 26.sp)
        Text(summary, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun GboardSettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(content = content)
    }
}
