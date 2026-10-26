package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

/** Sidebar appearance only. Other Security Center pages keep their vendor material. */
@Composable
internal fun SidebarGlassSettingsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val coreEnabled = masterEnabled && prefs.getBoolean(
        ConfigSchema.Glass.ENABLED.name(), ConfigSchema.Glass.ENABLED.uiDefault())
    val enableKey = ConfigSchema.Glass.SECURITY_CENTER_GLASS.name()
    var featureEnabled by remember { mutableStateOf(prefs.getBoolean(
        enableKey, ConfigSchema.Glass.SECURITY_CENTER_GLASS.uiDefault())) }
    var revision by remember { mutableIntStateOf(0) }
    val backgroundKeys = remember {
        SecurityCenterGlassPreferences.BACKGROUND_FIELDS.map {
            ThirdPartyGlassProfiles.key(SecurityCenterGlassPreferences.PROFILE_ID, it)
        }
    }
    val highlightKeys = remember {
        SecurityCenterGlassPreferences.HIGHLIGHT_FIELDS.map {
            ThirdPartyGlassProfiles.key(SecurityCenterGlassPreferences.PROFILE_ID, it)
        }
    }
    val hasBackground = revision.let { backgroundKeys.any(prefs::contains) }
    var inheritBackground by remember { mutableStateOf(!hasBackground) }
    val enabled = coreEnabled && featureEnabled

    fun resetBackground() {
        val e = prefs.edit()
        backgroundKeys.forEach { e.remove(it).remove("${it}_tenths") }
        e.apply()
        inheritBackground = true
        revision++
    }

    fun resetLighting() {
        val e = prefs.edit()
        highlightKeys.forEach { e.remove(it) }
        GlassEdgeEffects.KEYS.forEach { config ->
            val key = GlassEdgeEffects.key(GlassEdgeEffects.SECURITY_CENTER, config)
            e.remove(key).remove("${key}_tenths")
        }
        e.apply()
        revision++
    }

    fun resetAll() {
        val e = prefs.edit()
        backgroundKeys.forEach { e.remove(it).remove("${it}_tenths") }
        highlightKeys.forEach { e.remove(it) }
        GlassEdgeEffects.KEYS.forEach { config ->
            val key = GlassEdgeEffects.key(GlassEdgeEffects.SECURITY_CENTER, config)
            e.remove(key).remove("${key}_tenths")
        }
        e.apply()
        inheritBackground = true
        revision++
    }

    @Composable
    fun backgroundValue(
        field: String,
        title: String,
        fallback: ConfigKey<Int>,
        min: Float,
        max: Float,
        suffix: String = "",
        decimal: Boolean = false,
    ) {
        val key = ThirdPartyGlassProfiles.key(SecurityCenterGlassPreferences.PROFILE_ID, field)
        val inherited = prefs.all[fallback.name()]
        val base = when {
            fallback.name() == ConfigSchema.Glass.BLUR.name()
                    && prefs.contains("${fallback.name()}_tenths") ->
                (prefs.all["${fallback.name()}_tenths"] as? Number)?.toFloat()?.div(10f)
                    ?: fallback.uiDefault().toFloat()
            inherited is Number -> inherited.toFloat()
            else -> fallback.uiDefault().toFloat()
        }
        var value by remember(key, revision) {
            mutableFloatStateOf((prefs.all[key] as? Number)?.toFloat() ?: base)
        }
        SliderPreference(
            value = value.coerceIn(min, max),
            onValueChange = {
                val next = it.coerceIn(min, max)
                value = next
                if (decimal) prefs.edit().putFloat(key, next).apply()
                else prefs.edit().putInt(key, next.roundToInt()).apply()
            },
            title = title,
            summary = if (prefs.contains(key)) "侧边栏独立设置" else "继承全局",
            valueText = if (decimal) "${value} $suffix" else "${value.roundToInt()} $suffix",
            valueTextForPreview = { "${it.roundToInt()} $suffix" },
            enabled = enabled && !inheritBackground,
            valueRange = min..max,
            snapIncrement = if (decimal) 0.5f else 1f,
        )
    }

    @Composable
    fun profileHighlight(field: String, title: String, global: ConfigKey<Boolean>) {
        val key = ThirdPartyGlassProfiles.key(SecurityCenterGlassPreferences.PROFILE_ID, field)
        var checked by remember(key, revision) {
            mutableStateOf(prefs.getBoolean(
                key, prefs.getBoolean(global.name(), global.uiDefault())))
        }
        SwitchPreference(
            title = title,
            checked = checked,
            onCheckedChange = {
                checked = it
                prefs.edit().putBoolean(key, it).apply()
            },
            summary = if (prefs.contains(key)) "独立高光" else "继承全局高光",
            enabled = enabled,
        )
    }

    @Composable
    fun edgeToggle(config: ConfigKey<Boolean>, title: String) {
        val key = GlassEdgeEffects.key(GlassEdgeEffects.SECURITY_CENTER, config)
        var checked by remember(key, revision) {
            mutableStateOf(prefs.getBoolean(
                key, prefs.getBoolean(config.name(), config.uiDefault())))
        }
        SwitchPreference(
            title = title,
            summary = if (prefs.contains(key)) "侧边栏独立设置" else "继承全局",
            checked = checked,
            onCheckedChange = {
                checked = it
                prefs.edit().putBoolean(key, it).apply()
            },
            enabled = enabled,
        )
    }

    @Composable
    fun edgeNumber(config: ConfigKey<Int>, title: String, unit: String = "") {
        ScopedGlassSlider(
            prefs = prefs,
            key = GlassEdgeEffects.key(GlassEdgeEffects.SECURITY_CENTER, config),
            fallback = config,
            title = title,
            unit = unit,
            enabled = enabled,
            generation = revision,
            decimal = config.storageMode() == ConfigKey.StorageMode.DP_TENTHS,
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PageHeader(
            "侧边栏玻璃",
            "仅自定义模块接管的侧边栏 Dock 与工具箱玻璃。应用列表使用原生材质，不受这里的背景参数影响。",
        ) }
        item { SmallTitle("功能") }
        item {
            SettingsCard {
                SwitchPreference(
                    title = "侧边栏玻璃",
                    summary = "关闭后使用安全中心原生材质",
                    checked = featureEnabled,
                    onCheckedChange = {
                        featureEnabled = it
                        prefs.edit().putBoolean(enableKey, it).apply()
                    },
                    enabled = coreEnabled,
                )
            }
        }
        item { SmallTitle("背景") }
        item {
            SettingsCard {
                SwitchPreference(
                    title = "继承全局背景",
                    summary = "关闭后可以单独调整；打开后清除全部侧边栏背景覆盖",
                    checked = inheritBackground,
                    onCheckedChange = {
                        inheritBackground = it
                        if (it) resetBackground()
                    },
                    enabled = enabled,
                )
            }
        }
        val color = listOf(
            Triple("tint_r", "色调 R", ConfigSchema.Glass.TINT_RED),
            Triple("tint_g", "色调 G", ConfigSchema.Glass.TINT_GREEN),
            Triple("tint_b", "色调 B", ConfigSchema.Glass.TINT_BLUE),
            Triple("tint_alpha", "透明度", ConfigSchema.Glass.TINT_ALPHA),
        )
        item {
            SettingsCard { backgroundValue("blur", "模糊强度", ConfigSchema.Glass.BLUR,
                0f, 60f) }
        }
        color.forEach { (field, title, fallback) ->
            item(key = field) {
                SettingsCard { backgroundValue(field, title, fallback, 0f, 255f) }
            }
        }
        item {
            SettingsCard {
                val key = ThirdPartyGlassProfiles.key(
                    SecurityCenterGlassPreferences.PROFILE_ID, "corner_radius_dp")
                var radius by remember(key, revision) {
                    mutableFloatStateOf((prefs.all[key] as? Number)?.toFloat() ?: -1f)
                }
                SliderPreference(
                    value = radius,
                    onValueChange = {
                        radius = it.roundToInt().toFloat()
                        prefs.edit().putFloat(key, radius).apply()
                    },
                    title = "独立圆角",
                    summary = "−1 表示自动匹配原生圆角",
                    valueText = "${radius.roundToInt()} dp",
                    valueTextForPreview = { "${it.roundToInt()} dp" },
                    enabled = enabled && !inheritBackground,
                    valueRange = -1f..160f,
                    snapIncrement = 1f,
                )
            }
        }
        item { SmallTitle("背景采样（下次打开侧边栏生效）") }
        item { SettingsCard { backgroundValue("capture_scale_percent", "采样比例",
            ConfigSchema.Glass.PASSBLUR_CAPTURE_SCALE, 50f, 100f, "%") } }
        item { SettingsCard { backgroundValue("render_fps", "渲染帧率上限",
            ConfigSchema.Glass.PASSBLUR_RENDER_FPS, 0f, 60f, "Hz") } }
        item {
            SettingsCard {
                ArrowPreference(
                    title = "恢复默认（背景）",
                    summary = "清除模糊、色调、圆角与采样覆盖，重新跟随全局配置",
                    enabled = enabled && hasBackground,
                    onClick = ::resetBackground,
                )
            }
        }

        item { SmallTitle("Prismal 高光") }
        item { SettingsCard { profileHighlight("highlight_sky_haze",
            "Sky haze · 天空雾光", ConfigSchema.LauncherHighlight.LARGE_SKY_HAZE) } }
        item { SettingsCard { profileHighlight("highlight_caustics",
            "Caustics · 焦散", ConfigSchema.LauncherHighlight.LARGE_CAUSTICS) } }
        item { SettingsCard { profileHighlight("highlight_press_glow",
            "Press glow · 按压辉光", ConfigSchema.LauncherHighlight.LARGE_PRESS_GLOW) } }

        item { SmallTitle("Prismal 描边与阴影（保留既有安全中心配置）") }
        item { SettingsCard { edgeToggle(ConfigSchema.Glass.BACKLIT_ENABLED, "启用折射光照描边") } }
        listOf(
            Triple(ConfigSchema.Glass.BACKLIT_STRENGTH, "描边不透明度", "%"),
            Triple(ConfigSchema.Glass.BACKLIT_WIDTH, "细线宽度", "dp"),
            Triple(ConfigSchema.Glass.BACKLIT_EXTENT, "暗色区延伸范围", "%"),
            Triple(ConfigSchema.Glass.BACKLIT_SOFTNESS, "明暗过渡柔和度", "%"),
        ).forEach { (config, title, unit) ->
            item(key = config.name()) { SettingsCard { edgeNumber(config, title, unit) } }
        }
        item { SettingsCard { edgeToggle(ConfigSchema.Glass.OUTER_SHADOW_ENABLED, "启用整体外阴影") } }
        listOf(
            Triple(ConfigSchema.Glass.OUTER_SHADOW_RADIUS, "阴影柔和半径", "dp"),
            Triple(ConfigSchema.Glass.OUTER_SHADOW_OFFSET_Y, "基础垂直偏移", "dp"),
            Triple(ConfigSchema.Glass.OUTER_SHADOW_LIGHT_FOLLOW, "方向光响应", "%"),
            Triple(ConfigSchema.Glass.OUTER_SHADOW_ALPHA, "阴影不透明度", ""),
            Triple(ConfigSchema.Glass.OUTER_SHADOW_RED, "阴影红", ""),
            Triple(ConfigSchema.Glass.OUTER_SHADOW_GREEN, "阴影绿", ""),
            Triple(ConfigSchema.Glass.OUTER_SHADOW_BLUE, "阴影蓝", ""),
        ).forEach { (config, title, unit) ->
            item(key = config.name()) { SettingsCard { edgeNumber(config, title, unit) } }
        }
        item {
            SettingsCard {
                ArrowPreference(
                    title = "恢复默认（光照）",
                    summary = "清除高光、描边与阴影覆盖，继承全局",
                    enabled = enabled,
                    onClick = ::resetLighting,
                )
            }
        }
        item {
            SettingsCard {
                ArrowPreference(
                    title = "恢复默认（全部）",
                    summary = "仅重置侧边栏背景与光照；不修改总开关",
                    enabled = enabled,
                    onClick = ::resetAll,
                )
            }
        }
    }
}
