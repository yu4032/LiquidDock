package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hellovoid.liquiddock.config.ConfigSchema
import top.yukonga.miuix.kmp.basic.SmallTitle

// Dedicated Dock decoration pages. All existing dependency toggles, numeric
// slider controls, glass grouping and preference keys retain their owners.
@Composable
internal fun StrokePage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    var dockStroke by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.STROKE_ENABLED.name(), ConfigSchema.Dock.STROKE_ENABLED.uiDefault())) }
    var squircle by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.SQUIRCLE.name(), ConfigSchema.Dock.SQUIRCLE.uiDefault())) }
    var fillDiff by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.FILL_DIFF.name(), ConfigSchema.Dock.FILL_DIFF.uiDefault())) }
    DenseSettingsList(padding, "Dock描边") {
        item(key = "stroke-enabled") {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Dock.STROKE_ENABLED, "显示完整描边", "控制 Dock 边框与灯光", masterEnabled) { dockStroke = it }
            }
        }
        item(key = "stroke-squircle") {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Dock.SQUIRCLE, "方圆形连续曲线", "调整原生模糊 Dock 轮廓与描边；液态玻璃轮廓由液态玻璃页的连续圆角开关控制", masterEnabled) { squircle = it }
            }
        }
        item(key = "stroke-fill-diff") {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Dock.FILL_DIFF, "Fill-Diff 描边", "通过填充与挖空获得清晰抗锯齿", masterEnabled) { fillDiff = it }
            }
        }
        item(key = "stroke-colors-title") { SmallTitle("描边背景色") }
        items(strokeSpecs.filter { it.section == IntSection.StrokeBackground }, key = { it.key }) { spec ->
            SettingsCard {
                IntSetting(prefs, spec, masterEnabled && dockStroke)
            }
        }
        item(key = "stroke-geometry-title") { SmallTitle("方圆形与线宽") }
        items(strokeSpecs.filter { it.section == IntSection.StrokeGeometry }, key = { it.key }) { spec ->
            val enabled = when (spec.dependency) {
                "dock_stroke" -> dockStroke
                "squircle" -> squircle
                "fill_diff" -> fillDiff
                else -> true
            }
            SettingsCard {
                IntSetting(prefs, spec, masterEnabled && enabled)
            }
        }
    }
}

@Composable
internal fun ShadowPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    open: (Page) -> Unit,
) {
    val dockEnabled = prefs.getBoolean(ConfigSchema.Dock.ENABLED.name(), ConfigSchema.Dock.ENABLED.uiDefault())
    val glassEnabled = prefs.getBoolean(ConfigSchema.Glass.ENABLED.name(), ConfigSchema.Glass.ENABLED.uiDefault())
    val nativeShadowEnabled = masterEnabled && dockEnabled && !glassEnabled
    var dockShadow by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.SHADOW_ENABLED.name(), ConfigSchema.Dock.SHADOW_ENABLED.uiDefault())) }
    var strokeShadow by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.STROKE_SHADOW.name(), ConfigSchema.Dock.STROKE_SHADOW.uiDefault())) }
    DenseSettingsList(
        padding,
        "原生Dock阴影",
        "仅开启 Dock 自定义且关闭液态玻璃时生效。启用液态玻璃后，Dock 阴影改由 Prismal 渲染。",
    ) {
        item(key = "dock-shadow-enabled") {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Dock.SHADOW_ENABLED, "Dock 原生阴影", "仅 Dock 自定义开启且液态玻璃关闭时生效", nativeShadowEnabled) { dockShadow = it }
            }
        }
        item(key = "dock-optical-response") {
            SettingsCard {
                ArrowPreference(
                    title = "液态玻璃阴影",
                    summary = "独立调整 Prismal 外阴影与边缘描边；仅开启液态玻璃时生效",
                    enabled = masterEnabled && glassEnabled,
                    onClick = { open(Page.DockEdge) },
                )
            }
        }
        item(key = "stroke-shadow-enabled") {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Dock.STROKE_SHADOW, "原生描边阴影", "仅 Dock 自定义开启且液态玻璃关闭时生效", nativeShadowEnabled) { strokeShadow = it }
            }
        }
        items(shadowSpecs, key = { it.key }) { spec ->
            SettingsCard {
                IntSetting(prefs, spec, nativeShadowEnabled && when (spec.dependency) {
                    "dock_shadow" -> dockShadow
                    "stroke_shadow" -> strokeShadow
                    else -> true
                })
            }
        }
    }
}

