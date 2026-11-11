package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.hellovoid.liquiddock.config.ConfigSchema
import com.hellovoid.liquiddock.os4.Os4NativeGlassOptions
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text

internal fun LazyListScope.os4NativeGlassSettings(
    prefs: SharedPreferences,
    enabled: Boolean,
) {
    Os4NativeGlassOptions.groups.forEach { (group, fields) ->
        item(key = "os4-native-glass-title:$group") { SmallTitle(group) }
        items(fields, key = Os4NativeGlassOptions.Field::id) { field ->
            Os4NativeGlassFieldSetting(prefs, field, enabled)
        }
    }
}

@Composable
private fun Os4NativeGlassFieldSetting(
    prefs: SharedPreferences,
    field: Os4NativeGlassOptions.Field,
    enabled: Boolean,
) {
    val key = ConfigSchema.Glass.OS4_NATIVE_OVERRIDES.name()
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
                summary = if (overridden) "自定义此项" else "沿用桌面原生值",
                enabled = enabled,
                insideMargin = ModernPreferenceMargin,
            )
            if (overridden) {
                BasicComponent(
                    title = "数值",
                    enabled = enabled,
                    insideMargin = ModernPreferenceMargin,
                    endActions = {
                        SliderValueReadout(
                            text = Os4NativeGlassOptions.format(value),
                            enabled = enabled,
                            onClick = { editing = true },
                        )
                    },
                )
                ModernGlassSlider(
                    value = value.toFloat(),
                    onValueChange = { next ->
                        if (next.isFinite()) {
                            val rounded = (next * 100f).roundToInt() / 100.0
                            write(overrides + (field.id to rounded))
                        }
                    },
                    valueRange = field.min.toFloat()..field.max.toFloat(),
                    visibilityThreshold = 0.01f,
                    snapIncrement = 0.01f,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 6.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${Os4NativeGlassOptions.format(field.min)} – ${Os4NativeGlassOptions.format(field.max)}",
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    top.yukonga.miuix.kmp.basic.Button(
                        onClick = { write(overrides - field.id) },
                        enabled = enabled,
                        minWidth = 56.dp,
                        minHeight = 36.dp,
                        insideMargin = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp,
                            vertical = 4.dp,
                        ),
                    ) {
                        Text("还原原生")
                    }
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
