package com.hellovoid.liquiddock

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hellovoid.liquiddock.config.ConfigKey
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// A single observer updates only the changed preference key; long slider pages
// do not register listeners per control or re-read preferences on every frame.
internal val LocalSettingsPreferenceRevisions = staticCompositionLocalOf<Map<String, Int>> { emptyMap() }

// Shared page-level cells, numeric/boolean preference controls and lazily grouped
// Prismal surfaces. Their callbacks, state keys and rendering are intentionally
// the same as the original implementations.
/**
 * Keep at most three numeric settings in one native Prismal glass surface.
 * This avoids a separate blur/refraction GraphicsLayer per dense-list row,
 * while each small group remains a LazyColumn item that scrolls with its
 * border. Sliders, steppers, and their live setting callbacks are untouched.
 */
internal fun LazyListScope.groupedIntSettings(
    specs: List<IntSpec>,
    prefs: SharedPreferences,
    enabled: Boolean,
) {
    items(specs.chunked(3), key = { group -> "int-group:${group.first().key}" }) { group ->
        SettingsCard {
            group.forEachIndexed { index, spec ->
                if (index > 0) ModernListDivider()
                IntSetting(prefs, spec, enabled)
            }
        }
    }
}

/**
 * Long settings pages have ONE lazy, vertically scrolling root.
 * Each visible control owns its own moving glass card; the previous
 * viewport-fixed outer card made the border appear stationary while only
 * its children scrolled. Lazy item keys and off-screen disposal are retained.
 */
@Composable
internal fun DenseSettingsList(
    padding: PaddingValues,
    title: String,
    summary: String? = null,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!summary.isNullOrBlank()) item(key = "dense-page-summary") {
            PageHeader(title, summary)
        }
        content()
    }
}

@Composable
internal fun SettingsList(
    padding: PaddingValues,
    title: String,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageHeader(title, summary) }
        item { SettingsCard(content) }
    }
}

@Composable
internal fun PageHeader(@Suppress("UNUSED_PARAMETER") title: String, summary: String? = null) {
    if (summary.isNullOrBlank()) return
    Text(
        text = summary,
        modifier = Modifier.padding(
            start = 20.dp,
            end = 20.dp,
            top = 8.dp,
            bottom = 6.dp,
        ),
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.62f),
    )
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    ModernSurface(
        modifier = Modifier.padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        Column(content = content)
    }
}

@Composable
internal fun BooleanSetting(
    prefs: SharedPreferences, config: ConfigKey<Boolean>, title: String, summary: String? = null,
    enabled: Boolean = true, default: Boolean = config.uiDefault(), onChanged: (Boolean) -> Unit = {},
) {
    val key = config.name()
    val revision = LocalSettingsPreferenceRevisions.current[key] ?: 0
    var value by remember(key, revision) { mutableStateOf(prefs.getBoolean(key, default)) }
    SwitchPreference(
        checked = value,
        onCheckedChange = { value = it; prefs.edit().putBoolean(key, it).apply(); onChanged(it) },
        title = title,
        summary = summary,
        enabled = enabled,
    )
}

@Composable
internal fun IntSetting(
    prefs: SharedPreferences,
    spec: IntSpec,
    enabledOverride: Boolean? = null,
    steps: Int = 0,
    beforeSave: ((Float, () -> Unit) -> Unit)? = null,
    // Guarded settings may allow only preflight-free steps during an active drag.
    previewWriteAllowed: ((Float) -> Boolean)? = null,
) {
    val decimalDp = spec.isDecimal
    val context = LocalContext.current
    val maxValue = remember(spec.key, context) { spec.max(context) }
    val resetValue = remember(spec.key, maxValue) {
        spec.resetValue().coerceIn(spec.min.toFloat(), maxValue.toFloat())
    }
    // Reading the disk-backed preference on every drag/recomposition was wasteful:
    // remember() already owns the initial state until this setting leaves composition.
    val storedRevision = LocalSettingsPreferenceRevisions.current[spec.key] ?: 0
    var value by remember(spec.key, maxValue) {
        val initial = (if (decimalDp && prefs.contains("${spec.key}_tenths"))
            prefs.getInt("${spec.key}_tenths", (resetValue * 10f).roundToInt()) / 10f
        else prefs.getInt(spec.key, resetValue.roundToInt()).toFloat())
            .coerceIn(spec.min.toFloat(), maxValue.toFloat())
        mutableStateOf(initial)
    }
    LaunchedEffect(spec.key, maxValue, storedRevision) {
        // Reread only the externally changed key; normal drag recomposition is pure state.
        if (storedRevision > 0) {
            value = (if (decimalDp && prefs.contains("${spec.key}_tenths"))
                prefs.getInt("${spec.key}_tenths", (resetValue * 10f).roundToInt()) / 10f
            else prefs.getInt(spec.key, resetValue.roundToInt()).toFloat())
                .coerceIn(spec.min.toFloat(), maxValue.toFloat())
        }
    }
    var editingValue by remember(spec.key) { mutableStateOf(false) }
    // The thumb remains continuous, but the displayed quantized preview becomes
    // the persisted value immediately when allowed by the setting's safety policy.
    var sliderPreview by remember(spec.key) { mutableStateOf<Float?>(null) }
    val enabled = enabledOverride ?: spec.dependency?.let { prefs.getBoolean(it, false) } ?: true

    fun save(nextValue: Float) {
        val next = if (decimalDp) {
            (nextValue * 10f).roundToInt() / 10f
        } else {
            nextValue.roundToInt().toFloat()
        }
        val bounded = next.coerceIn(spec.min.toFloat(), maxValue.toFloat())
        if (bounded == value) return // already persisted this quantized value
        val persist = {
            value = bounded
            val editor = prefs.edit().putInt(spec.key, bounded.roundToInt())
            if (decimalDp) editor.putInt("${spec.key}_tenths", (bounded * 10f).roundToInt())
            editor.apply()
        }
        if (beforeSave != null) beforeSave(bounded, persist) else persist()
    }

    val displayValue = remember(value, decimalDp) {
        if (decimalDp) {
            String.format(java.util.Locale.ROOT, "%.1f", value)
        } else {
            value.roundToInt().toString()
        }
    }
    val previewLabel = sliderPreview?.let { draft ->
        if (decimalDp) String.format(java.util.Locale.ROOT, "%.1f", draft)
        else draft.roundToInt().toString()
    }
    val displayText = "${previewLabel ?: displayValue}${if (spec.unit.isBlank()) "" else " ${spec.unit}"}"
    val scaledValue = if (decimalDp) (value * 10f).roundToInt() else value.roundToInt()
    val scaledMin = if (decimalDp) spec.min * 10 else spec.min
    val scaledMax = if (decimalDp) maxValue * 10 else maxValue
    val sliderSteps = if (steps > 0) steps else
        DiscreteSliderSteps.forStoragePrecision(spec.min, maxValue, decimalDp)
    // The thumb remains continuous while dragging; its displayed/persisted
    // values use storage precision without allocating excessive native tick marks.
    val snapIncrement = if (decimalDp) 0.1f else 1f

    Column(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = spec.title,
            summary = spec.summary,
            enabled = enabled,
            insideMargin = ModernPreferenceMargin,
            endActions = {
                Text(
                    text = displayText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled) { editingValue = true }
                        .padding(horizontal = 8.dp, vertical = 9.dp),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            },
        )

        ModernGlassSlider(
            value = value,
            onValueChange = ::save,
            valueRange = spec.min.toFloat()..maxValue.toFloat(),
            visibilityThreshold = if (decimalDp) 0.1f else 1f,
            steps = sliderSteps,
            snapIncrement = snapIncrement,
            onValuePreview = { preview ->
                sliderPreview = preview
                if (preview != null && enabled &&
                    (beforeSave == null || previewWriteAllowed?.invoke(preview) == true)
                ) {
                    // Same quantized value as the right-hand label; save() suppresses
                    // repeated writes for frames in the same displayed step.
                    save(preview)
                }
            },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { save(resetValue) },
                enabled = enabled && kotlin.math.abs(value - resetValue) > 0.0001f,
                minWidth = 56.dp,
                minHeight = 36.dp,
                insideMargin = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                prismalNumericAction = true,
            ) {
                Text("重置")
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 4.dp))
            ModernGlassStepper(
                value = scaledValue,
                valueRange = scaledMin..scaledMax,
                enabled = enabled,
                onValueChange = { next ->
                    save(if (decimalDp) next / 10f else next.toFloat())
                },
            )
        }
    }
    NumericSettingInputDialog(
        visible = editingValue,
        title = spec.title,
        currentText = displayValue,
        valueRange = spec.min.toFloat()..maxValue.toFloat(),
        integerOnly = !decimalDp,
        onDismiss = { editingValue = false },
        onConfirm = { next ->
            save(next)
            editingValue = false
        },
    )
}
