package com.hellovoid.liquiddock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.styropyr0.prismal.components.PrismalGlassStepper
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// All four numeric composables and the typed input parser are moved unchanged.
// Their existing Prismal drag, numeric preview live-write, MIUIX fallback,
// focus/IME, reset, min/max and stepper behavior remain owned by this module.

/**
 * Parse direct numeric input without silently storing an out-of-range or
 * fractional integer value. Persistence and quantization remain with the
 * existing setting's own callback.
 */
internal fun parseNumericSettingInput(
    raw: String,
    integerOnly: Boolean,
    min: Float,
    max: Float,
): Float? {
    val normalized = raw.trim().replace(',', '.')
    val value = if (integerOnly) {
        normalized.toIntOrNull()?.toFloat()
    } else {
        normalized.toFloatOrNull()
    }
    return value?.takeIf { it.isFinite() && it in min..max }
}

@Composable
internal fun NumericSettingInputDialog(
    visible: Boolean,
    title: String,
    currentText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    integerOnly: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var entered by remember(title) { mutableStateOf(TextFieldValue(currentText)) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) {
            entered = TextFieldValue(currentText, selection = TextRange(0, currentText.length))
        }
    }
    val parsed = parseNumericSettingInput(
        entered.text, integerOnly, valueRange.start, valueRange.endInclusive,
    )
    AnimatedSettingsWindowDialog(
        show = visible,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            val minText = if (integerOnly) valueRange.start.roundToInt().toString() else valueRange.start.toString()
            val maxText = if (integerOnly) valueRange.endInclusive.roundToInt().toString() else valueRange.endInclusive.toString()
            Text(
                text = "允许范围：$minText – $maxText",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            )
            top.yukonga.miuix.kmp.basic.TextField(
                value = entered,
                onValueChange = { entered = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(
                    keyboardType = when {
                        valueRange.start < 0f -> KeyboardType.Text
                        integerOnly -> KeyboardType.Number
                        else -> KeyboardType.Decimal
                    },
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { parsed?.let(onConfirm) },
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = onDismiss,
                    minWidth = 72.dp,
                    minHeight = 40.dp,
                ) {
                    Text("取消")
                }
                Spacer(Modifier.padding(horizontal = 5.dp))
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = { parsed?.let(onConfirm) },
                    enabled = parsed != null,
                    minWidth = 72.dp,
                    minHeight = 40.dp,
                ) {
                    Text("确定")
                }
            }
        }
    }
}

@Composable
internal fun SliderPreference(
    value: Float,
    onValueChange: (Float) -> Unit,
    title: String,
    summary: String? = null,
    valueText: String = "",
    valueTextForPreview: ((Float) -> String)? = null,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    snapIncrement: Float = 0f,
    endActions: @Composable (() -> Unit)? = null,
    insideMargin: PaddingValues = ModernPreferenceMargin,
) {
    val backdrop = LocalTouchPrismalBackdrop.current
    val currentValue by rememberUpdatedState(value)
    var editingValue by remember(title) { mutableStateOf(false) }
    var previewValue by remember(title) { mutableStateOf<Float?>(null) }
    val intervals = (steps + 1).coerceAtLeast(1)
    val stepSize = ((valueRange.endInclusive - valueRange.start) / intervals)
        .takeIf { it > 0f } ?: 0.01f
    fun quantize(raw: Float): Float = DiscreteSliderSteps.snap(
        raw, valueRange.start, valueRange.endInclusive, steps, snapIncrement,
    )
    val motionThreshold = if (steps > 0 || snapIncrement <= 0f) stepSize else snapIncrement
    val sliderEnabledState = rememberUpdatedState(enabled)
    val sliderCallbackState = rememberUpdatedState(onValueChange)
    val quantizeState = rememberUpdatedState<(Float) -> Float>({ raw -> quantize(raw) })
    // Only dispatch when the number shown at the right changes. The track/thumb
    // continue moving freely; persistence receives the displayed snapped value.
    val lastLiveStep = remember(title) { arrayOfNulls<Float>(1) }
    val stableSliderValue = remember { { currentValue } }
    val stableSliderChange: (Float) -> Unit = remember {
        { next ->
            val snapped = quantizeState.value(next)
            if (sliderEnabledState.value && lastLiveStep[0] != snapped) {
                sliderCallbackState.value(snapped)
            }
            lastLiveStep[0] = null
        }
    }
    val stableSliderPreview: (Float?) -> Unit = remember {
        { next ->
            previewValue = next
            if (next == null) {
                lastLiveStep[0] = null
            } else if (sliderEnabledState.value && lastLiveStep[0] != next) {
                lastLiveStep[0] = next
                sliderCallbackState.value(next)
            }
        }
    }
    // Miuix keeps a continuous visual draft, but persists its displayed
    // quantized value while the finger is moving, not only after release.
    var nativeDragging by remember { mutableStateOf(false) }
    var nativeDraft by remember { mutableFloatStateOf(value) }
    val shownValueText = previewValue?.let { valueTextForPreview?.invoke(it) } ?: valueText

    Column {
        BasicComponent(
            title = title,
            summary = summary,
            enabled = enabled,
            insideMargin = insideMargin,
            endActions = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (shownValueText.isNotBlank()) {
                        Text(
                            text = shownValueText,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = enabled) { editingValue = true }
                                .padding(horizontal = 8.dp, vertical = 9.dp),
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.66f),
                            fontSize = 13.sp,
                        )
                    }
                    endActions?.invoke()
                }
            },
        )
        if (backdrop != null) {
            GuiOnTouchPrismalSlider(
                value = stableSliderValue,
                onValueChange = stableSliderChange,
                valueRange = valueRange,
                visibilityThreshold = motionThreshold,
                backdrop = backdrop,
                steps = steps,
                snapIncrement = snapIncrement,
                onValuePreview = stableSliderPreview,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 14.dp)
                    .alpha(if (enabled) 1f else 0.42f),
            )
        } else {
            top.yukonga.miuix.kmp.basic.Slider(
                value = if (nativeDragging) nativeDraft else currentValue,
                onValueChange = { next ->
                    if (enabled) {
                        nativeDraft = next
                        nativeDragging = true
                        stableSliderPreview(quantizeState.value(next))
                    }
                },
                onValueChangeFinished = {
                    if (nativeDragging) {
                        val released = nativeDraft
                        nativeDragging = false
                        stableSliderChange(released)
                        stableSliderPreview(null)
                    }
                },
                valueRange = valueRange,
                steps = 0, // Native track stays continuous; only its values snap.
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
            )
        }
    }
    val inputStep = if (steps > 0 || snapIncrement <= 0f) stepSize else snapIncrement
    val integerOnly = valueRange.start % 1f == 0f &&
        valueRange.endInclusive % 1f == 0f && inputStep % 1f == 0f
    NumericSettingInputDialog(
        visible = editingValue,
        title = title,
        currentText = if (integerOnly) value.roundToInt().toString() else value.toString(),
        valueRange = valueRange,
        integerOnly = integerOnly,
        onDismiss = { editingValue = false },
        onConfirm = { next ->
            onValueChange(quantize(next))
            editingValue = false
        },
    )
}

@Composable
internal fun ModernGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    snapIncrement: Float = 0f,
    onValuePreview: (Float?) -> Unit = {},
) {
    val backdrop = LocalTouchPrismalBackdrop.current
    val currentValue by rememberUpdatedState(value)
    val sliderEnabledState = rememberUpdatedState(enabled)
    val sliderCallbackState = rememberUpdatedState(onValueChange)
    val previewCallbackState = rememberUpdatedState(onValuePreview)
    val stableSliderPreview: (Float?) -> Unit = remember {
        { next -> previewCallbackState.value(next) }
    }
    val stableSliderValue = remember { { currentValue } }
    val stableSliderChange: (Float) -> Unit = remember {
        { next -> if (sliderEnabledState.value) sliderCallbackState.value(next) }
    }
    var nativeDragging by remember { mutableStateOf(false) }
    var nativeDraft by remember { mutableFloatStateOf(value) }
    if (backdrop != null) {
        GuiOnTouchPrismalSlider(
            value = stableSliderValue,
            onValueChange = stableSliderChange,
            valueRange = valueRange,
            visibilityThreshold = visibilityThreshold,
            backdrop = backdrop,
            enabled = enabled,
            steps = steps,
            snapIncrement = snapIncrement,
            onValuePreview = stableSliderPreview,
            modifier = modifier.alpha(if (enabled) 1f else 0.42f),
        )
    } else {
        top.yukonga.miuix.kmp.basic.Slider(
            value = if (nativeDragging) nativeDraft else currentValue,
            onValueChange = { next ->
                if (enabled) {
                    nativeDraft = next
                    nativeDragging = true
                    previewCallbackState.value(DiscreteSliderSteps.snap(
                        next, valueRange.start, valueRange.endInclusive,
                        steps, snapIncrement,
                    ))
                }
            },
            onValueChangeFinished = {
                if (nativeDragging) {
                    val nearest = DiscreteSliderSteps.snap(
                        nativeDraft, valueRange.start, valueRange.endInclusive,
                        steps, snapIncrement,
                    )
                    nativeDragging = false
                    sliderCallbackState.value(nearest)
                    previewCallbackState.value(null)
                }
            },
            valueRange = valueRange,
            steps = 0, // Apply quantization only after release, not on pointer movement.
            enabled = enabled,
            modifier = modifier,
        )
    }
}

@Composable
internal fun ModernGlassStepper(
    value: Int,
    valueRange: IntRange,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The ordinary body surface backdrop is intentionally null. Restore
    // native Prismal +/- through the same glass-only source used by sliders.
    val backdrop = LocalTouchPrismalBackdrop.current
    if (backdrop != null) {
        PrismalGlassStepper(
            value = value,
            onValueChange = { if (enabled) onValueChange(it) },
            backdrop = backdrop,
            valueRange = valueRange,
            repeatOnHold = true,
            modifier = modifier.alpha(if (enabled) 1f else 0.42f),
        )
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { onValueChange((value - 1).coerceAtLeast(valueRange.first)) },
                enabled = enabled && value > valueRange.first,
                minWidth = 36.dp,
                minHeight = 36.dp,
                insideMargin = PaddingValues(0.dp),
            ) { Text("−") }
            Button(
                onClick = { onValueChange((value + 1).coerceAtMost(valueRange.last)) },
                enabled = enabled && value < valueRange.last,
                minWidth = 36.dp,
                minHeight = 36.dp,
                insideMargin = PaddingValues(0.dp),
            ) { Text("+") }
        }
    }
}
