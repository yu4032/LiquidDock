package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import com.hellovoid.liquiddock.config.ConfigSchema
import com.hellovoid.liquiddock.os4.Os4NativeGlassOptions
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text

/**
 * 按柔光玻璃模型的分节渲染原生字段：`sections` 为 (小节标题 -> 字段 id 列表)，
 * 字段按 id 从 `Os4NativeGlassOptions.fields` 取出。空列表不渲染任何字段——
 * 哪些页面承载原生字段由调用方（`liquidNativeSections`）显式声明。
 */
internal fun LazyListScope.os4NativeGlassSettings(
    prefs: SharedPreferences,
    enabled: Boolean,
    sections: List<Pair<String, List<String>>>,
    overrideKey: String = ConfigSchema.Glass.OS4_NATIVE_OVERRIDES.name(),
) {
    val byId = Os4NativeGlassOptions.fields.associateBy(Os4NativeGlassOptions.Field::id)
    sections.forEach { (title, ids) ->
        val fields = ids.mapNotNull(byId::get)
        if (fields.isEmpty()) return@forEach
        item(key = "os4-native-glass-title:$overrideKey:$title") { SmallTitle(title) }
        items(fields, key = { "${overrideKey}:${it.id}" }) { field ->
            Os4NativeGlassFieldSetting(prefs, field, enabled, overrideKey)
        }
    }
}

@Composable
private fun Os4NativeGlassFieldSetting(
    prefs: SharedPreferences,
    field: Os4NativeGlassOptions.Field,
    enabled: Boolean,
    overrideKey: String = ConfigSchema.Glass.OS4_NATIVE_OVERRIDES.name(),
) {
    val key = overrideKey
    val revision = LocalSettingsPreferenceRevisions.current[key] ?: 0
    var overrides by remember(key, revision) {
        mutableStateOf(
            Os4NativeGlassOptions.parse(prefs.getString(key, "").orEmpty()) ?: emptyMap(),
        )
    }
    val overridden = field.id in overrides
    val value = overrides[field.id] ?: field.default
    var editing by remember(field.id) { mutableStateOf(false) }

    fun write(next: Map<String, Double>) {
        val serialized = Os4NativeGlassOptions.serialize(next) ?: return
        overrides = next
        prefs.edit().putString(key, serialized).apply()
    }

    SettingsCard {
        Column {
            SwitchPreference(
                checked = overridden,
                onCheckedChange = { checked ->
                    if (checked) write(overrides + (field.id to field.default))
                    else write(overrides - field.id)
                },
                title = field.label,
                summary = "${field.summary} · ${if (overridden) "已自定义" else "沿用原生"}（重启桌面生效）",
                enabled = enabled,
                insideMargin = ModernPreferenceMargin,
            )
            if (overridden) {
                val integerStep = field.max - field.min >= 50.0
                val step = if (integerStep) 1.0 else 0.01
                val scaledMin = ceil(field.min / step - 1e-6).toInt()
                val scaledMax = floor(field.max / step + 1e-6).toInt()
                var sliderPreview by remember(field.id) { mutableStateOf<Float?>(null) }
                val shownValue = sliderPreview?.toDouble() ?: value
                val scaledValue = (value / step).roundToInt().coerceIn(scaledMin, scaledMax)
                val sliderSteps = ((field.max - field.min) / step).roundToInt().coerceIn(0, 200)
                BasicComponent(
                    title = "数值",
                    summary = "${Os4NativeGlassOptions.format(field.min)} – ${Os4NativeGlassOptions.format(field.max)}",
                    enabled = enabled,
                    insideMargin = ModernPreferenceMargin,
                    endActions = {
                        SliderValueReadout(
                            text = Os4NativeGlassOptions.format(shownValue),
                            enabled = enabled,
                            onClick = { editing = true },
                        )
                    },
                )
                ModernGlassSlider(
                    value = value.toFloat(),
                    onValueChange = { next ->
                        if (next.isFinite()) {
                            val rounded = ((next / step).roundToInt() * step * 100.0).roundToInt() / 100.0
                            write(overrides + (field.id to rounded.coerceIn(field.min, field.max)))
                        }
                    },
                    valueRange = field.min.toFloat()..field.max.toFloat(),
                    visibilityThreshold = step.toFloat(),
                    steps = sliderSteps,
                    snapIncrement = step.toFloat(),
                    onValuePreview = { preview ->
                        sliderPreview = preview
                        if (preview != null && enabled && preview.isFinite()) {
                            val rounded = ((preview / step).roundToInt() * step * 100.0).roundToInt() / 100.0
                            write(overrides + (field.id to rounded.coerceIn(field.min, field.max)))
                        }
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    top.yukonga.miuix.kmp.basic.Button(
                        onClick = { write(overrides - field.id) },
                        enabled = enabled,
                        minWidth = 56.dp,
                        minHeight = 36.dp,
                        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("还原原生")
                    }
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    ModernGlassStepper(
                        value = scaledValue,
                        valueRange = scaledMin..scaledMax,
                        enabled = enabled,
                        onValueChange = { next ->
                            val stepped = ((next * step) * 100.0).roundToInt() / 100.0
                            write(overrides + (field.id to stepped.coerceIn(field.min, field.max)))
                        },
                    )
                }
            }
        }
    }
    NumericSettingInputDialog(
        visible = editing,
        title = field.label,
        currentText = Os4NativeGlassOptions.format(value),
        valueRange = field.min.toFloat()..field.max.toFloat(),
        integerOnly = false,
        onDismiss = { editing = false },
        onConfirm = { next ->
            val rounded = (next * 100f).roundToInt() / 100.0
            write(overrides + (field.id to rounded))
            editing = false
        },
    )
}

@Composable
internal fun SoftGlassScopedPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    title: String,
    summary: String,
    customKey: com.hellovoid.liquiddock.config.ConfigKey<Boolean>,
    overridesKey: com.hellovoid.liquiddock.config.ConfigKey<String>,
) {
    val glassRevision = LocalSettingsPreferenceRevisions.current[ConfigSchema.Glass.ENABLED.name()] ?: 0
    val glassEnabled = remember(glassRevision) {
        prefs.getBoolean(
            ConfigSchema.Glass.ENABLED.name(),
            ConfigSchema.Glass.ENABLED.uiDefault(),
        )
    }
    val customRevision = LocalSettingsPreferenceRevisions.current[customKey.name()] ?: 0
    var customEnabled by remember(customKey.name(), customRevision) {
        mutableStateOf(prefs.getBoolean(customKey.name(), customKey.uiDefault()))
    }
    val allSections = remember {
        buildList {
            liquidNativeSections[Page.SoftGlassMaterial]?.let(::addAll)
            liquidNativeSections[Page.SoftGlassColor]?.let(::addAll)
            liquidNativeSections[Page.SoftGlassLighting]?.let(::addAll)
        }
    }
    val active = masterEnabled && glassEnabled

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "scoped-header") {
            PageHeader(title, summary)
        }
        item(key = "scoped-controls") {
            SettingsCard {
                BooleanSetting(
                    prefs = prefs,
                    config = customKey,
                    title = "独立调节参数",
                    summary = "关闭时跟随最外部柔光玻璃全局基准；开启后独立调整该组件参数。",
                    enabled = active,
                ) { customEnabled = it }
                ModernListDivider()
                BasicComponent(
                    title = "应用全局设置",
                    summary = "将最外部柔光玻璃的当前参数复制到此组件（重启桌面生效）",
                    enabled = active,
                    insideMargin = ModernPreferenceMargin,
                    onClick = {
                        val globalRaw = prefs.getString(
                            ConfigSchema.Glass.OS4_NATIVE_OVERRIDES.name(),
                            "",
                        ).orEmpty()
                        prefs.edit()
                            .putString(overridesKey.name(), globalRaw)
                            .putBoolean(customKey.name(), true)
                            .apply()
                        customEnabled = true
                    },
                )
            }
        }
        os4NativeGlassSettings(
            prefs = prefs,
            enabled = active && customEnabled,
            sections = allSections,
            overrideKey = overridesKey.name(),
        )
    }
}
