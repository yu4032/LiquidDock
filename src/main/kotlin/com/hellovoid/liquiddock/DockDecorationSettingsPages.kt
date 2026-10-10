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
    DenseSettingsList(padding, "描边") {
        item(key = "stroke-enabled") {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Dock.STROKE_ENABLED, "显示完整描边", "控制 Dock 边框与灯光", masterEnabled) { dockStroke = it }
            }
        }
        item(key = "stroke-squircle") {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Dock.SQUIRCLE, "方圆形连续曲线", "iPad 风格连续圆角", masterEnabled) { squircle = it }
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
internal fun ShadowPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    val dockEnabled = prefs.getBoolean(ConfigSchema.Dock.ENABLED.name(), ConfigSchema.Dock.ENABLED.uiDefault())
    var dockShadow by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.SHADOW_ENABLED.name(), ConfigSchema.Dock.SHADOW_ENABLED.uiDefault())) }
    var strokeShadow by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.STROKE_SHADOW.name(), ConfigSchema.Dock.STROKE_SHADOW.uiDefault())) }
    DenseSettingsList(padding, "阴影") {
        item(key = "dock-shadow-enabled") {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Dock.SHADOW_ENABLED, "整个 Dock 下方阴影", "跟随 Dock 长宽、高度和圆角", masterEnabled && dockEnabled) { dockShadow = it }
            }
        }
        item(key = "stroke-shadow-enabled") {
            SettingsCard {
                BooleanSetting(prefs, ConfigSchema.Dock.STROKE_SHADOW, "描边阴影", "描边下方的柔和阴影", masterEnabled && dockEnabled) { strokeShadow = it }
            }
        }
        items(shadowSpecs, key = { it.key }) { spec ->
            SettingsCard {
                IntSetting(prefs, spec, masterEnabled && dockEnabled && when (spec.dependency) {
                    "dock_shadow" -> dockShadow
                    "stroke_shadow" -> strokeShadow
                    else -> true
                })
            }
        }
    }
}

